package br.ufpe.cin.concurrency.fjdetector.refactors;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.PrimitiveType;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ListRewrite;

import br.ufpe.cin.concurrency.fjdetector.Refactor;

public class ForkJoinCopiedPatternRefactor implements Refactor {

	private ASTRewrite rewriter;

	public ForkJoinCopiedPatternRefactor(ASTRewrite rewriter) {
		this.rewriter = rewriter;
	}

	@Override
	public void refactor(ASTNode node) {
		AST ast = node.getAST();

		FieldDeclaration from = createNewField(ast, "from");
		FieldDeclaration to = createNewField(ast, "to");
		
		ListRewrite listRewrite = rewriter.getListRewrite(node, TypeDeclaration.BODY_DECLARATIONS_PROPERTY);
		listRewrite.insertFirst(from, null);
		listRewrite.insertAfter(to, from, null);
		
		TypeDeclaration clazz = ((TypeDeclaration) node);
		
		for(MethodDeclaration method: clazz.getMethods()) {
			if(method.isConstructor()) {
				String className = clazz.resolveBinding().getName();
				MethodDeclaration constructor = createPrivateConstructor(ast, className, method);
				listRewrite.insertAfter(constructor, to, null);
				break;
			}
		}
	}

	private MethodDeclaration createPrivateConstructor(AST ast, String className, MethodDeclaration methodConstructor) {
		//remove public modifier
		methodConstructor.modifiers().remove(0);
		methodConstructor.modifiers().add(ast.newModifier(Modifier.ModifierKeyword.PRIVATE_KEYWORD));
		System.out.println(methodConstructor.getBody());
		
		// constructor parameters
		SingleVariableDeclaration from = createNewParam(ast, "from");
		methodConstructor.parameters().add(from);

		SingleVariableDeclaration to = createNewParam(ast, "to");
		methodConstructor.parameters().add(to);
		
		Block block = methodConstructor.getBody();
		createNewAssignment(ast, block, "from");
		createNewAssignment(ast, block, "to");
		
		return methodConstructor;
	}

	private void createNewAssignment(AST ast, Block block, String varName) {
		Assignment a = ast.newAssignment();
		a.setOperator(Assignment.Operator.ASSIGN);
		block.statements().add(ast.newExpressionStatement(a));
		FieldAccess fa = ast.newFieldAccess();
		fa.setExpression(ast.newThisExpression());
		fa.setName(ast.newSimpleName(varName));
		a.setLeftHandSide(fa);
		a.setRightHandSide(ast.newSimpleName(varName));
	}

	private SingleVariableDeclaration createNewParam(AST ast, String name) {
		SingleVariableDeclaration newParam = ast.newSingleVariableDeclaration();
		newParam.setType(ast.newPrimitiveType(PrimitiveType.INT));
		newParam.setName(ast.newSimpleName(name));
		return newParam;
	}

	private FieldDeclaration createNewField(AST ast, String name) {
		VariableDeclarationFragment frag = ast.newVariableDeclarationFragment();
		frag.setName(ast.newSimpleName(name));
		FieldDeclaration newFieldDecl = ast.newFieldDeclaration(frag);
		newFieldDecl.modifiers().add(ast.newModifier(Modifier.ModifierKeyword.PRIVATE_KEYWORD));
		newFieldDecl.setType(ast.newPrimitiveType(PrimitiveType.INT));
		return newFieldDecl;
	}
}
