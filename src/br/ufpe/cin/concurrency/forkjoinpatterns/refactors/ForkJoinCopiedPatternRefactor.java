package br.ufpe.cin.concurrency.forkjoinpatterns.refactors;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.PrimitiveType;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ListRewrite;

import br.ufpe.cin.concurrency.forkjoinpatterns.Refactor;

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
		
		String className = ((TypeDeclaration) node).resolveBinding().getName();
		MethodDeclaration constructor = createPrivateConstructor(ast, className);
		listRewrite.insertAfter(constructor, to, null);
	}

	private MethodDeclaration createPrivateConstructor(AST ast, String className) {
		
		MethodDeclaration methodConstructor = ast.newMethodDeclaration();
		methodConstructor.setConstructor(true);
		methodConstructor.modifiers().add(ast.newModifier(Modifier.ModifierKeyword.PRIVATE_KEYWORD));
		methodConstructor.setName(ast.newSimpleName(className));
		// classType.bodyDeclarations().add(methodConstructor);

		// constructor parameters
		SingleVariableDeclaration variableDeclaration = createNewParam(ast, "from");
		methodConstructor.parameters().add(variableDeclaration);

		variableDeclaration = createNewParam(ast, "to");
		methodConstructor.parameters().add(variableDeclaration);
		
		Block constructorBlock = ast.newBlock();
		methodConstructor.setBody(constructorBlock);
		
		return methodConstructor;
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
