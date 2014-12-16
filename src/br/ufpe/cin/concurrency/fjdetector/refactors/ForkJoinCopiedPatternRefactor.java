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

	private final ASTRewrite rewriter;

	public ForkJoinCopiedPatternRefactor(ASTRewrite rewriter) {
		this.rewriter = rewriter;
	}

	@Override
	public void refactor(ASTNode node) {
		TypeDeclaration clazz = ((TypeDeclaration) node);
		
		FieldDeclaration lastField = getLastFieldDeclared(clazz);
		
		FieldDeclaration from = createNewField(node, "from");
		FieldDeclaration to = createNewField(node, "to");
		
		ListRewrite listRewrite = rewriter.getListRewrite(node, TypeDeclaration.BODY_DECLARATIONS_PROPERTY);
		listRewrite.insertAfter(from, lastField, null);
		listRewrite.insertAfter(to, from, null);
		
		MethodDeclaration constructor = createPrivateConstructor(node);
		listRewrite.insertAfter(constructor, to, null);
	}

	private MethodDeclaration createPrivateConstructor(ASTNode node) {
		AST ast = node.getAST();
		TypeDeclaration clazz = ((TypeDeclaration) node);
		for(MethodDeclaration method: clazz.getMethods()) {
			if(method.isConstructor()) {
		
				// remove public modifier
				method.modifiers().remove(0);
				method.modifiers().add(ast.newModifier(Modifier.ModifierKeyword.PRIVATE_KEYWORD));

				// constructor parameters
				SingleVariableDeclaration from = createNewParam(node, "from");
				method.parameters().add(from);

				SingleVariableDeclaration to = createNewParam(node, "to");
				method.parameters().add(to);

				Block block = method.getBody();
				createNewAssignment(node, block, "from");
				createNewAssignment(node, block, "to");

				return method;
			}
		}
		throw new UnsupportedOperationException(String.format("Class %s does not have a constructor definer!", clazz));
	}

	private void createNewAssignment(ASTNode node, Block block, String varName) {
		AST ast = node.getAST();
		Assignment a = ast.newAssignment();
		a.setOperator(Assignment.Operator.ASSIGN);
		block.statements().add(ast.newExpressionStatement(a));
		FieldAccess fa = ast.newFieldAccess();
		fa.setExpression(ast.newThisExpression());
		fa.setName(ast.newSimpleName(varName));
		a.setLeftHandSide(fa);
		a.setRightHandSide(ast.newSimpleName(varName));
	}

	private SingleVariableDeclaration createNewParam(ASTNode node, String name) {
		AST ast = node.getAST();
		SingleVariableDeclaration newParam = ast.newSingleVariableDeclaration();
		newParam.setType(ast.newPrimitiveType(PrimitiveType.INT));
		newParam.setName(ast.newSimpleName(name));
		return newParam;
	}

	private FieldDeclaration createNewField(ASTNode node, String name) {
		AST ast = node.getAST();
		VariableDeclarationFragment frag = ast.newVariableDeclarationFragment();
		frag.setName(ast.newSimpleName(name));
		FieldDeclaration newFieldDecl = ast.newFieldDeclaration(frag);
		newFieldDecl.modifiers().add(ast.newModifier(Modifier.ModifierKeyword.PRIVATE_KEYWORD));
		newFieldDecl.setType(ast.newPrimitiveType(PrimitiveType.INT));
		return newFieldDecl;
	}
	
	private FieldDeclaration getLastFieldDeclared(TypeDeclaration clazz) {
		int total = clazz.getFields().length - 1;
		return clazz.getFields()[total];
	}
}
