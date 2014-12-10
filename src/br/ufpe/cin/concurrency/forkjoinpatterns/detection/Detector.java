package br.ufpe.cin.concurrency.forkjoinpatterns.detection;

import java.util.Set;

import br.ufpe.cin.concurrency.forkjoinpatterns.util.PrintableString;

public interface Detector {

	public Set<PrintableString> getResults();
}
