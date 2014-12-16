package br.ufpe.cin.concurrency.forkjoinpatterns.fix;

import org.eclipse.ltk.core.refactoring.Refactoring;
import org.eclipse.ltk.ui.refactoring.RefactoringWizard;

public class ForkJoinMisuseFixWizard extends RefactoringWizard  {

	public ForkJoinMisuseFixWizard(Refactoring refactoring, int flags) {
		super(refactoring, flags);
	}
	
	public ForkJoinMisuseFixWizard(
            ForkJoinMisuseFix refactoring, String string) {
        super(refactoring, DIALOG_BASED_USER_INTERFACE | PREVIEW_EXPAND_FIRST_NODE);
        setDefaultPageTitle(string);
    }

	
	@Override
	protected void addUserInputPages() {
		// TODO Auto-generated method stub
		
	}

}
