package br.ufpe.cin.concurrency.forkjoinpatterns.actions;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.internal.corext.refactoring.util.RefactoringASTParser;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.ITreeSelection;
import org.eclipse.ltk.ui.refactoring.RefactoringWizard;
import org.eclipse.ltk.ui.refactoring.RefactoringWizardOpenOperation;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IActionDelegate;
import org.eclipse.ui.IObjectActionDelegate;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;

import br.ufpe.cin.concurrency.forkjoinpatterns.detectors.ForkJoinCopiedPatternDetector;
import br.ufpe.cin.concurrency.forkjoinpatterns.fix.ForkJoinMisuseFix;
import br.ufpe.cin.concurrency.forkjoinpatterns.fix.ForkJoinMisuseFixWizard;
import br.ufpe.cin.concurrency.forkjoinpatterns.util.Result;
import br.ufpe.cin.concurrency.forkjoinpatterns.view.ResultViewer;

public class ForkJoinMisusesAction implements IObjectActionDelegate {

    private Shell shell;
    private IJavaProject project;

    /**
     * Constructor for Action1.
     */
    public ForkJoinMisusesAction() {
        super();
    }

    /**
     * @see IObjectActionDelegate#setActivePart(IAction, IWorkbenchPart)
     */
    @Override
    public void setActivePart(IAction action, IWorkbenchPart targetPart) {
        this.shell = targetPart.getSite().getShell();
    }

    /**
     * @see IActionDelegate#run(IAction)
     */
    @Override
    public void run(IAction action) {
        if (project != null) {
            try {
                IPackageFragmentRoot[] roots = project.getAllPackageFragmentRoots();
                IPackageFragment[] packages = getPackageFragmentsInRoots(roots);
                ICompilationUnit[] units = getCompilationUnitInPackages(packages);
                                
                List<Result> detections = new ArrayList<Result>();
                for (ICompilationUnit unit: units) {
                    CompilationUnit root = new RefactoringASTParser(AST.JLS3).parse(unit, true);

                    ASTRewrite rewriter = ASTRewrite.create(root.getAST());
                    
                    ForkJoinCopiedPatternDetector copied = new ForkJoinCopiedPatternDetector(rewriter);
                    root.accept(copied);
                    detections.addAll(copied.getResults());
                    
                    if (copied.isRefactoringAvailable()) {
                    	ForkJoinMisuseFix fix = new ForkJoinMisuseFix(unit, rewriter);
                        run(new ForkJoinMisuseFixWizard(fix, "ForkJoin Fix"), shell, "ForkJoin Fix");
                    }
                }
                
                IWorkbenchPage page = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage();
                ResultViewer viewer = (ResultViewer) page.showView(ResultViewer.ID);

                viewer.clearData();
                viewer.setInput(detections);
                
                MessageDialog.openInformation(shell, "Detecting ForkJoin misuses",
                        "Detecting ForkJoin misuses finished.");
            } catch (JavaModelException | PartInitException e) {
                e.printStackTrace();
            }
        }
    }

    private void run(RefactoringWizard wizard, Shell parent, String dialogTitle) {
        try {
            RefactoringWizardOpenOperation operation = new RefactoringWizardOpenOperation(
                    wizard);
            operation.run(parent, dialogTitle);
        } catch (InterruptedException exception) {
            // Do nothing
        }
    }

    /**
     * @see IActionDelegate#selectionChanged(IAction, ISelection)
     */
    @Override
    public void selectionChanged(IAction action, ISelection selection) {
        if (selection instanceof ITreeSelection) {
            this.project = (IJavaProject) ((ITreeSelection) selection)
                    .getPaths()[0].getFirstSegment();
        }
    }

    private IPackageFragment[] getPackageFragmentsInRoots(
            IPackageFragmentRoot[] roots) throws JavaModelException {
        List frags = new ArrayList();
        for (int i = 0; i < roots.length; i++) {
            if (roots[i].getKind() == IPackageFragmentRoot.K_SOURCE) {
                IPackageFragmentRoot root = roots[i];
                IJavaElement[] rootFragments = root.getChildren();
                for (int j = 0; j < rootFragments.length; j++) {
                    frags.add(rootFragments[j]);
                }
            }
        }
        IPackageFragment[] fragments = new IPackageFragment[frags.size()];
        frags.toArray(fragments);
        return fragments;
    }

    private ICompilationUnit[] getCompilationUnitInPackages(
            IPackageFragment[] packages) throws JavaModelException {
        List frags = new ArrayList();
        for (int i = 0; i < packages.length; i++) {
            IPackageFragment p = packages[i];
            ICompilationUnit[] units = p.getCompilationUnits();
            for (int j = 0; j < units.length; j++) {
                frags.add(units[j]);
            }
        }
        ICompilationUnit[] fragments = new ICompilationUnit[frags.size()];
        frags.toArray(fragments);
        return fragments;
    }
}
