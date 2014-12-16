package br.ufpe.cin.concurrency.forkjoinpatterns.refactors;

import org.eclipse.jdt.core.dom.ASTNode;

public interface Refactor {

	void refactor(ASTNode node);
	
}
