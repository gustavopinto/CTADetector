package br.ufpe.cin.concurrency.fjdetector.fix;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.refactoring.IJavaRefactorings;
import org.eclipse.jdt.core.refactoring.descriptors.JavaRefactoringDescriptor;
import org.eclipse.jdt.internal.corext.refactoring.changes.DynamicValidationRefactoringChange;
import org.eclipse.jdt.internal.corext.refactoring.util.TextChangeManager;
import org.eclipse.ltk.core.refactoring.Change;
import org.eclipse.ltk.core.refactoring.Refactoring;
import org.eclipse.ltk.core.refactoring.RefactoringDescriptor;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.eclipse.ltk.core.refactoring.TextChange;
import org.eclipse.text.edits.MultiTextEdit;

public class ForkJoinMisuseFix extends Refactoring {

	private ICompilationUnit unit;
	private ASTRewrite rewrite;
	
	public ForkJoinMisuseFix(ICompilationUnit unit, ASTRewrite rewrite) {
        this.unit = unit;
        this.rewrite = rewrite;
    }
	@Override
	public RefactoringStatus checkFinalConditions(IProgressMonitor arg0)
			throws CoreException, OperationCanceledException {
		return new RefactoringStatus();
	}

	@Override
	public RefactoringStatus checkInitialConditions(IProgressMonitor arg0)
			throws CoreException, OperationCanceledException {
		return new RefactoringStatus();
	}

	@Override
	public Change createChange(IProgressMonitor pm) throws CoreException,
			OperationCanceledException {
        IJavaProject javaProject = unit.getJavaProject();
        String project = null;
        if (javaProject != null)
            project = javaProject.getElementName();
        int flags = JavaRefactoringDescriptor.JAR_MIGRATION | JavaRefactoringDescriptor.JAR_REFACTORING | RefactoringDescriptor.STRUCTURAL_CHANGE | RefactoringDescriptor.MULTI_CHANGE;

        String description = "Fix ForkJoin usage";
        String comment = "Fix ForkJoin usage";
        
        final JavaRefactoringDescriptor descriptor= new JavaRefactoringDescriptor(IJavaRefactorings.ENCAPSULATE_FIELD, project, description, comment, new HashMap(), flags) {};
        
        final DynamicValidationRefactoringChange result= new DynamicValidationRefactoringChange(descriptor, getName());
        TextChangeManager changeManager = new TextChangeManager();
        TextChange change= changeManager.get(unit);
        MultiTextEdit root= new MultiTextEdit();
        change.setEdit(root);
        
        root.addChild(rewrite.rewriteAST());
        TextChange[] changes= changeManager.getAllChanges();
        pm.beginTask("", changes.length);
        pm.setTaskName("ConvertToConcurrentHashMap: create changes");
        for (int i= 0; i < changes.length; i++) {
            result.add(changes[i]);
            pm.worked(1);
        }
        pm.done();
        return result;
	}

	@Override
	public String getName() {
		return "Fix ForkJoin Misuse";
	}
}
