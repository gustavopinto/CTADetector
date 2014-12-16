package br.ufpe.cin.concurrency.fjdetector.util;

import java.util.HashSet;
import java.util.Set;

import org.eclipse.jdt.core.dom.MethodInvocation;

public class BlackList {

	private static Set<String> methods = new HashSet<String>();
	
	static {
		methods.add("Arrays.copyOfRange");
	}
	
	public static boolean has(MethodInvocation method) {
		if(method.getExpression() == null) return false;
		String fullyqualifiedname = String.format("%s.%s", method.getExpression(), method.getName());
		return methods.contains(fullyqualifiedname);
	}
}