package br.ufpe.cin.concurrency.forkjoinpatterns;

import java.util.Set;

import br.ufpe.cin.concurrency.forkjoinpatterns.util.Result;

public interface Detector {

	public Set<Result> getResults();
	
	public boolean isRefactoringAvailable();
}
