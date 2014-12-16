package br.ufpe.cin.concurrency.forkjoinpatterns.actions;

import java.util.ArrayList;
import java.util.Set;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.CompilationUnit;
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
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.console.ConsolePlugin;
import org.eclipse.ui.console.IConsole;
import org.eclipse.ui.console.MessageConsole;

import br.ufpe.cin.concurrency.forkjoinpatterns.detectors.AtomicViolationPatternForConcurrentHashMap;
import br.ufpe.cin.concurrency.forkjoinpatterns.detectors.CollectVariableInfo;
import br.ufpe.cin.concurrency.forkjoinpatterns.detectors.CorrectDetectForMap;
import br.ufpe.cin.concurrency.forkjoinpatterns.detectors.LazyInitializationPattern;
import br.ufpe.cin.concurrency.forkjoinpatterns.detectors.SemanticPatternForConcurrentHashMap;
import br.ufpe.cin.concurrency.forkjoinpatterns.util.Results;
import br.ufpe.cin.concurrency.forkjoinpatterns.util.Result;
import br.ufpe.cin.concurrency.forkjoinpatterns.view.ResultViewer;

public class CorrectDetectionAction implements IObjectActionDelegate {

    private Shell shell;
    IJavaProject javaproject;
    public static boolean isRewrite;

    /**
     * Constructor for Action1.
     */
    public CorrectDetectionAction() {
        super();
    }

    /**
     * @see IObjectActionDelegate#setActivePart(IAction, IWorkbenchPart)
     */
    public void setActivePart(IAction action, IWorkbenchPart targetPart) {
        shell = targetPart.getSite().getShell();
    }

    /**
     * @see IActionDelegate#run(IAction)
     */
    public void run(IAction action) {
        if (javaproject != null) {
            try {
                IPackageFragmentRoot[] roots = javaproject
                        .getAllPackageFragmentRoots();
                IPackageFragment[] packages = getPackageFragmentsInRoots(roots);
                ICompilationUnit[] units = getCompilationUnitInPackages(packages);
                                
                ArrayList<Result> allResult = new ArrayList<Result>();
                for (int i = 0; i < units.length; i++) {
                    Set<Result> results;
                    CompilationUnit root = new RefactoringASTParser(AST.JLS3)
                            .parse(units[i], true);
                    
                    CollectVariableInfo info = new CollectVariableInfo();
                    root.accept(info);

                    SemanticPatternForConcurrentHashMap semanticAnalyzer = new SemanticPatternForConcurrentHashMap(
                            info, units[i], null);
                    root.accept(semanticAnalyzer);
                    
                    AtomicViolationPatternForConcurrentHashMap avMapAnalyzer = new AtomicViolationPatternForConcurrentHashMap(
                          info, units[i], null);
                    root.accept(avMapAnalyzer);
                            
                    CorrectDetectForMap correct = new CorrectDetectForMap(info, avMapAnalyzer.buggyLocations);
                    root.accept(correct);
                    results = correct.getResults();
//                    PrintUtils.printResult("Correct Usage", results);
                    allResult.addAll(results);
                    
                    LazyInitializationPattern correctLI = new LazyInitializationPattern(info);
                    root.accept(correctLI);
                    results = correctLI.getCorrectResults();
//                    PrintUtils.printResult("Correct Usage", results);
                    allResult.addAll(results);
                }
                
//                ResultViewer.viewer.setInput(allResult);
//                ResultViewer.viewer.refresh();
                
                MessageDialog.openInformation(shell, "Correct check-then-act idioms",
                        "Correct idioms detection finished.");
            } catch (JavaModelException e) {
                e.printStackTrace();
            }
        }
    }

    public void run(RefactoringWizard wizard, Shell parent, String dialogTitle) {
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
    public void selectionChanged(IAction action, ISelection selection) {
        if (selection instanceof ITreeSelection) {
            this.javaproject = (IJavaProject) ((ITreeSelection) selection)
                    .getPaths()[0].getFirstSegment();
        }
    }

    public IPackageFragment[] getPackageFragmentsInRoots(
            IPackageFragmentRoot[] roots) throws JavaModelException {
        ArrayList frags = new ArrayList();
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

    public ICompilationUnit[] getCompilationUnitInPackages(
            IPackageFragment[] packages) throws JavaModelException {
        ArrayList frags = new ArrayList();
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
