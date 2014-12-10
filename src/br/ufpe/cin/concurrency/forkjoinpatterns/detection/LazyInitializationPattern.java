package br.ufpe.cin.concurrency.forkjoinpatterns.detection;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.eclipse.core.resources.IFile;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.IfStatement;
import org.eclipse.jdt.core.dom.InfixExpression;
import org.eclipse.jdt.core.dom.InfixExpression.Operator;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.NullLiteral;
import org.eclipse.jdt.core.dom.ParenthesizedExpression;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.internal.corext.dom.ASTNodes;
import org.eclipse.jdt.internal.corext.dom.Bindings;

import br.ufpe.cin.concurrency.forkjoinpatterns.util.BindingUtils;
import br.ufpe.cin.concurrency.forkjoinpatterns.util.PrintUtils;
import br.ufpe.cin.concurrency.forkjoinpatterns.util.PrintableString;

public class LazyInitializationPattern extends ASTVisitor {
    private IBinding fFieldBinding;
    Set<PrintableString> results = new HashSet<PrintableString>();
    Set<PrintableString> correctResults = new HashSet<PrintableString>();
    CollectVariableInfo info;

    public LazyInitializationPattern(CollectVariableInfo in) {
        info = in;
    }
    
    @Override
    public boolean visit(IfStatement ifStatement) {
        Expression ifExpression = ifStatement.getExpression();

        // Remove the parentheses.
        while (ifExpression instanceof ParenthesizedExpression) {
            ifExpression = ((ParenthesizedExpression) ifExpression)
                    .getExpression();
        }

        if (ifExpression instanceof InfixExpression) {
            handleOperatorConditional(ifStatement, ifExpression);
        }

        return true;
    }

    protected void handleOperatorConditional(IfStatement ifStatement,
            Expression ifExpression) {
        Operator operator = ((InfixExpression) ifExpression).getOperator();
        InfixExpression testExpression = (InfixExpression) ifExpression;
        Expression leftOperand = testExpression.getLeftOperand();
        Expression rightOperand = testExpression.getRightOperand();

        if (rightOperand instanceof NullLiteral
                && (leftOperand instanceof SimpleName || leftOperand instanceof QualifiedName)) {
            fFieldBinding = ((Name) leftOperand).resolveBinding();
            if (fFieldBinding == null
                    || !info.getFieldsSet().contains(fFieldBinding))
                return;
            if (operator.equals(InfixExpression.Operator.EQUALS)) {
                Statement stmt = ifStatement.getThenStatement();
                List<Assignment> stmts = new ArrayList<Assignment>();
                stmt.accept(new AssignmentCollector(stmts));
                handleStmts(stmts);
            } else if (operator.equals(InfixExpression.Operator.NOT_EQUALS)) {
                Statement stmt = ifStatement.getElseStatement();
                List<Assignment> stmts = new ArrayList<Assignment>();
                if (stmt == null) {
                    ASTNode parentB = ASTNodes.getParent(ifStatement,
                            Block.class);
                    List parentBStmts = ((Block) parentB).statements();
                    AssignmentCollector collector = new AssignmentCollector(
                            stmts);
                    int index = parentBStmts.indexOf(ifStatement);
                    if (index >= 0) {
                        Iterator stmtIt = parentBStmts.listIterator(index);
                        while (stmtIt.hasNext()) {
                            ASTNode node = (ASTNode) stmtIt.next();
                            if (!node.equals(ifStatement))
                                node.accept(collector);
                        }
                    }
                } else {
                    stmt.accept(new AssignmentCollector(stmts));
                }
                handleStmts(stmts);
            }
        }
    }

    private void handleStmts(List<Assignment> stmts) {
        Iterator<Assignment> it = stmts.iterator();
        while (it.hasNext()) {
            Assignment assign = it.next();
            Expression leftOperand = assign.getLeftHandSide();
            Expression rightOperand = assign.getRightHandSide();
            if (leftOperand instanceof Name
                    && Bindings.equals(fFieldBinding,
                            ((Name) leftOperand).resolveBinding())
                    && rightOperand instanceof ClassInstanceCreation) {
                ClassInstanceCreation creation = (ClassInstanceCreation) rightOperand;
                String typeName = creation.getType().toString();
                typeName = typeName.contains("<") ? typeName.substring(0,
                        typeName.indexOf('<')) : typeName;
                if (BindingUtils.allType.contains(typeName)) {
                    CompilationUnit unit = (CompilationUnit) assign.getRoot();
                    Object[] className = PrintUtils.getClassNameAndLine(unit,
                            assign);
                    if (!BindingUtils.hasSynchronized(assign)) {
                        results.add(new PrintableString(
                                "if(v == null) { v = new C(); } ",
                                (String) className[0], (String) className[1],
                                (IFile) className[2], false));
                    } else {
                        correctResults.add(new PrintableString(
                                "Lazy Initialization", (String) className[0],
                                (String) className[1], (IFile) className[2],
                                false));
                    }
                }
            }
        }
    }

    public Set<PrintableString> getResults() {
        return results;
    }

    public Set<PrintableString> getCorrectResults() {
        return correctResults;
    }

    private class AssignmentCollector extends ASTVisitor {
        List<Assignment> assigns;

        AssignmentCollector(List<Assignment> l) {
            assigns = l;
        }

        @Override
        public boolean visit(Assignment assign) {
            assigns.add(assign);
            return true;
        }
    }
}
