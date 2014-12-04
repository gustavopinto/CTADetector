package concurrencypatterns.fix;


import org.eclipse.ltk.core.refactoring.Refactoring;
import org.eclipse.ltk.ui.refactoring.RefactoringWizard;

public class ConcurrentCollectionFixWizard  extends RefactoringWizard {
    public ConcurrentCollectionFixWizard(Refactoring refactoring, int flags) {
        super(refactoring, flags);
    }

    public ConcurrentCollectionFixWizard(
            ConcurrentCollectionFix refactoring, String string) {
        super(refactoring, DIALOG_BASED_USER_INTERFACE | PREVIEW_EXPAND_FIRST_NODE);
        setDefaultPageTitle(string);
    }

    @Override
    protected void addUserInputPages() {

    }
}
