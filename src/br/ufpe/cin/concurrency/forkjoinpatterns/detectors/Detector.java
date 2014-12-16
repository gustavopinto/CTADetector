package br.ufpe.cin.concurrency.forkjoinpatterns.detectors;

import java.util.Set;

import br.ufpe.cin.concurrency.forkjoinpatterns.util.PrintableString;

public interface Detector {

	public Set<PrintableString> getResults();
}
