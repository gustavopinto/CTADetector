package br.ufpe.cin.concurrency.forkjoinpatterns.util;

import org.eclipse.core.resources.IFile;

public class Result {
	private String javaProject;
	private String className;
	private String blackList;
	private String line;
	private IFile file;

	public Result(String javaProject, String className, String blackList, int line, IFile file) {
		this.javaProject = javaProject;
		this.className = className;
		this.blackList = blackList;
		this.line = String.valueOf(line);
		this.file = file;
		
	}

	@Deprecated
	public Result(String string, String string2, String string3,
			IFile iFile, boolean hasSynchronized) {
		// TODO Auto-generated constructor stub
	}

	public String getClassName() {
		return className;
	}

	public String getBlackList() {
		return blackList;
	}

	public String getLine() {
		return line;
	}

	public IFile getFile() {
		return file;
	}
	
	public String getProjectName() {
		return javaProject;
	}
	
	@Override
	public boolean equals(Object o) {
		if (o instanceof Result) {
			Result ps2 = (Result) o;
			String s1 = className + line;
			String s2 = ps2.className + ps2.line;
			return s1.equals(s2);
		}
		return false;
	}

	@Override
	public int hashCode() {
		return (className + line).hashCode();
	}

	public String toString() {
		return className + " " + blackList + ": " + line;
	}
}
