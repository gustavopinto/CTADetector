package concurrencypatterns.util;

import java.util.List;

import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.ExpressionStatement;
import org.eclipse.jdt.core.dom.ReturnStatement;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;

public class StatementVisitor extends ASTVisitor {
    List stmts;

    public StatementVisitor(List l) {
        stmts = l;
    }

    @Override
    public boolean visit(VariableDeclarationFragment vdf) {
        stmts.add(vdf);
        return true;
    }

    @Override
    public boolean visit(ExpressionStatement estmt) {
        stmts.add(estmt);
        return true;
    }

    @Override
    public boolean visit(ReturnStatement rstmt) {
        stmts.add(rstmt);
        return true;
    }
}
