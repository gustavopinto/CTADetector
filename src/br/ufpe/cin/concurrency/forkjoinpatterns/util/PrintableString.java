package br.ufpe.cin.concurrency.forkjoinpatterns.util;

import org.eclipse.core.resources.IFile;
import org.eclipse.jdt.ui.actions.ProjectActionGroup;

public class PrintableString {
	private String javaProject;
	private String className;
	private String blackList;
	private String line;
	private IFile file;

	public PrintableString(String javaProject, String className, String blackList, int line, IFile file) {
		this.javaProject = javaProject;
		this.className = className;
		this.blackList = blackList;
		this.line = String.valueOf(line);
		this.file = file;
		
	}

	@Deprecated
	public PrintableString(String string, String string2, String string3,
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
		if (o instanceof PrintableString) {
			PrintableString ps2 = (PrintableString) o;
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
