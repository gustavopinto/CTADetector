package concurrentpatterns.detection;

import java.util.Set;

import concurrencypatterns.util.PrintableString;

public interface Detector {

	public Set<PrintableString> getResults();
}
