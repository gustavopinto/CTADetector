package br.ufpe.cin.concurrency.fjdetector.util;

import org.eclipse.core.resources.IFile;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.MethodInvocation;

public class Results {
	
	public static Result getMetadata(MethodInvocation node) {
		CompilationUnit unit = (CompilationUnit) node.getRoot();
		
		String javaProject = unit.getJavaElement().getJavaProject().getProject().getName();
		String className = unit.getJavaElement().getElementName();
		int line = unit.getLineNumber(node.getStartPosition());
		
		IFile file = null;
		try {
			file = (IFile) unit.getJavaElement().getCorrespondingResource();
		} catch (JavaModelException e) {
			e.printStackTrace();
		}

		String blackList = String.format("%s.%s", node.getExpression(), node.getName());
		
		return new Result(javaProject, className, blackList, line, file);
    }
}
