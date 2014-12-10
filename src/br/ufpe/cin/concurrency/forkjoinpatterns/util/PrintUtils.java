package br.ufpe.cin.concurrency.forkjoinpatterns.util;

import java.util.Set;

import org.eclipse.core.resources.IFile;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.ui.console.MessageConsoleStream;

import br.ufpe.cin.concurrency.forkjoinpatterns.actions.PatternDetectionAction;

public class PrintUtils {
    public static Object[] getClassNameAndLine(CompilationUnit unit, ASTNode n) {
        IType fileName = unit.getTypeRoot().findPrimaryType();
        String s = (fileName == null) ? "" : fileName.toString();
        // String className = (s.indexOf('[') == -1) ? s : s.substring(0,
        // s.indexOf('['));
        String className = unit.getJavaElement().getPath().toString();
        IFile file = null;
        try {
            file = (IFile) unit.getJavaElement()
                    .getCorrespondingResource();
        } catch (JavaModelException e) {
            e.printStackTrace();
        }

        int line = unit.getLineNumber(n.getStartPosition());
        Object[] nameLine = { className, String.valueOf(line), file};
        return nameLine;
    }

    public static void printResult(String bugType, Set<PrintableString> results) {
        for (PrintableString s : results) {
            if (s.isSync()) {
                s.setIdiomType("Performance error");
            } else {
                s.setIdiomType(bugType);
            }
        }
    }
}
