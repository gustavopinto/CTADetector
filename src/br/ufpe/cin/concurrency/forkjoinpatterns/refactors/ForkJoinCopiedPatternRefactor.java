package br.ufpe.cin.concurrency.forkjoinpatterns.refactors;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.PrimitiveType;
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
