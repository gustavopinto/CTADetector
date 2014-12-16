package br.ufpe.cin.concurrency.forkjoinpatterns.refactors;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;

public class ForkJoinCopiedPatternRefactor implements Refactor {

	private ASTRewrite rewriter;
	
	public ForkJoinCopiedPatternRefactor(ASTRewrite rewriter) {
		this.rewriter = rewriter;
	}

	@Override
	public void refactor(ASTNode node) {
		rewriter.remove(node, null);
	}
}
