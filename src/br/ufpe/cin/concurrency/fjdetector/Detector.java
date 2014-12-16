package br.ufpe.cin.concurrency.fjdetector;

import java.util.Set;

import br.ufpe.cin.concurrency.fjdetector.util.Result;

public interface Detector {

	public Set<Result> getResults();
	
	public boolean isRefactoringAvailable();
}
