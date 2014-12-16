package br.ufpe.cin.concurrency.fjdetector.detectors;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import org.eclipse.core.resources.IFile;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.CastExpression;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ConditionalExpression;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ExpressionStatement;
import org.eclipse.jdt.core.dom.ForStatement;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.IfStatement;
import org.eclipse.jdt.core.dom.InfixExpression;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.NullLiteral;
import org.eclipse.jdt.core.dom.InfixExpression.Operator;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.NumberLiteral;
import org.eclipse.jdt.core.dom.ParenthesizedExpression;
import org.eclipse.jdt.core.dom.PrefixExpression;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.ReturnStatement;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;
import org.eclipse.jdt.core.dom.WhileStatement;
import org.eclipse.jdt.internal.corext.dom.ASTNodes;
import org.eclipse.ui.console.MessageConsoleStream;

import br.ufpe.cin.concurrency.fjdetector.actions.PatternDetectionAction;
import br.ufpe.cin.concurrency.fjdetector.util.BindingUtils;
import br.ufpe.cin.concurrency.fjdetector.util.RefactoringUtil;
import br.ufpe.cin.concurrency.fjdetector.util.Result;
import br.ufpe.cin.concurrency.fjdetector.util.Results;

public class AtomicViolationPatternForQueue extends ASTVisitor {
    protected CollectVariableInfo info;
    private IBinding fFieldBinding;
    private Set<String> methodName = new HashSet<String>();
    Set<Result> results = new HashSet<Result>();

    private ASTRewrite rewriter;

    final static protected int ISEMPTY_FIX = 0;

    private ASTNode tmpIfWhileStmt;
    private Expression tmpCondition;
    private ASTNode tmpVDecl;
    private boolean isConditionalExpression;

    public AtomicViolationPatternForQueue(CollectVariableInfo i, ASTRewrite rw) {
        info = i;
        rewriter = rw;
        methodName.add("poll");
        methodName.add("peek");
        methodName.add("remove");
        methodName.add("take");
    }

    @Override
    public boolean visit(WhileStatement whileStmt) {
        Expression whileExpression = whileStmt.getExpression();

        // Remove the parentheses.
        while (whileExpression instanceof ParenthesizedExpression) {
            whileExpression = ((ParenthesizedExpression) whileExpression)
                    .getExpression();
        }

        if (whileExpression instanceof InfixExpression) {
            // Operator conditional: if(something operator something)
            Set<Expression> set = seperateInfixExpression((InfixExpression) whileExpression);
            for (Expression e : set) {
                if (e instanceof InfixExpression)
                    handleOperatorConditional(whileStmt, e);
                else
                    handleDirectConditional(whileStmt, e);
            }
        } else {
            // Direct conditional: if(something)
            handleDirectConditional(whileStmt, whileExpression);
        }

        return true;
    }

    @Override
    public boolean visit(ForStatement forStmt) {
        Expression forExpression = forStmt.getExpression();

        // Remove the parentheses.
        while (forExpression instanceof ParenthesizedExpression) {
            forExpression = ((ParenthesizedExpression) forExpression)
                    .getExpression();
        }

        if (forExpression instanceof InfixExpression) {
            // Operator conditional: if(something operator something)
            Set<Expression> set = seperateInfixExpression((InfixExpression) forExpression);
            for (Expression e : set) {
                if (e instanceof InfixExpression)
                    handleOperatorConditional(forStmt, e);
                else
                    handleDirectConditional(forStmt, e);
            }
        } else {
            // Direct conditional: if(something)
            handleDirectConditional(forStmt, forExpression);
        }

        return true;
    }

    @Override
    public boolean visit(ConditionalExpression conditionalExpression) {

        Expression e = conditionalExpression.getExpression();

        // Remove the parentheses.
        while (e instanceof ParenthesizedExpression) {
            e = ((ParenthesizedExpression) e).getExpression();
        }

        if (e instanceof InfixExpression) {
            Set<Expression> set = seperateInfixExpression((InfixExpression) e);
            // Operator conditional: if(something operator something)
            for (Expression eset : set) {
                if (eset instanceof InfixExpression)
                    handleOperatorConditional(conditionalExpression, eset);
                else
                    handleDirectConditional(conditionalExpression, eset);
            }
        } else {
            // Direct conditional: if(something)
            handleDirectConditional(conditionalExpression, e);
        }

        return true;
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
            Set<Expression> set = seperateInfixExpression((InfixExpression) ifExpression);
            // Operator conditional: if(something operator something)
            for (Expression e : set) {
                if (e instanceof InfixExpression)
                    handleOperatorConditional(ifStatement, e);
                else
                    handleDirectConditional(ifStatement, e);
            }
        } else {
            // Direct conditional: if(something)
            handleDirectConditional(ifStatement, ifExpression);
        }

        return true;
    }

    protected void handleOperatorConditional(ASTNode ifStatement,
            Expression ifExpression) {

        Operator operator = ((InfixExpression) ifExpression).getOperator();
        InfixExpression testExpression = (InfixExpression) ifExpression;
        Expression leftOperand = testExpression.getLeftOperand();
        Expression rightOperand = testExpression.getRightOperand();

        if (operator.equals(Operator.GREATER)
                && rightOperand instanceof NumberLiteral
                && ((NumberLiteral) rightOperand).getToken().equals("0")) {
            // if(size() > 0)
            while (leftOperand instanceof ParenthesizedExpression) {
                leftOperand = ((ParenthesizedExpression) leftOperand)
                        .getExpression();
            }

            if (leftOperand instanceof MethodInvocation) {
                // if(size() > 0)
                tmpIfWhileStmt = ifStatement;
                tmpCondition = testExpression;
                isConditionalExpression = (ifStatement instanceof ConditionalExpression);
                handle0Conditional(ifStatement, (MethodInvocation) leftOperand,
                        false);
            } else if (leftOperand instanceof SimpleName
                    || leftOperand instanceof QualifiedName) {
                // variable = size(); if(variable > 0)
                String name = (leftOperand instanceof SimpleName) ? ((SimpleName) leftOperand)
                        .getFullyQualifiedName()
                        : ((QualifiedName) leftOperand).getFullyQualifiedName();
                IBinding binding = BindingUtils.resolveBinding(leftOperand);

                ASTNode variableInitializer = BindingUtils.getInitializer(
                        ifStatement, binding, name, info);

                if (variableInitializer instanceof CastExpression)
                    variableInitializer = ((CastExpression) variableInitializer)
                            .getExpression();

                if (variableInitializer instanceof MethodInvocation) {
                    tmpIfWhileStmt = ifStatement;
                    tmpCondition = testExpression;
                    tmpVDecl = ASTNodes.getParent(variableInitializer,
                            Statement.class);
                    isConditionalExpression = (ifStatement instanceof ConditionalExpression);
                    handle0Conditional(ifStatement,
                            (MethodInvocation) variableInitializer, true);
                }
            }
        }
    }

    protected void handleDirectConditional(ASTNode ifStatement,
            Expression ifExpression) {

        // Remove the prefix but record its presence.
        if (ifExpression instanceof PrefixExpression) {
            PrefixExpression negationExpression = (PrefixExpression) ifExpression;
            if (negationExpression.getOperator().equals(
                    PrefixExpression.Operator.NOT)) {
                ifExpression = negationExpression.getOperand();
                // Remove the parentheses.
                while (ifExpression instanceof ParenthesizedExpression) {
                    ifExpression = ((ParenthesizedExpression) ifExpression)
                            .getExpression();
                }

                // if(something) or if(!something)
                if (ifExpression instanceof MethodInvocation) {
                    // if(!method())
                    // if(!isEmpty())
                    tmpIfWhileStmt = ifStatement;
                    tmpCondition = ifExpression;
                    isConditionalExpression = (ifStatement instanceof ConditionalExpression);
                    MethodInvocation mapInvocation;
                    mapInvocation = (MethodInvocation) ifExpression;
                    handleBothDirectCondition(ifStatement, mapInvocation, false);
                } else if (ifExpression instanceof SimpleName) {
                    // if(!variable)
                    SimpleName booleanVariableCheck = (SimpleName) ifExpression;
                    ASTNode booleanVariableInitializer = BindingUtils
                            .getInitializer(ifStatement, booleanVariableCheck
                                    .resolveBinding(), booleanVariableCheck
                                    .getFullyQualifiedName(), info);

                    if (booleanVariableInitializer instanceof MethodInvocation) {
                        tmpIfWhileStmt = ifStatement;
                        tmpCondition = ifExpression;
                        tmpVDecl = ASTNodes.getParent(
                                booleanVariableInitializer, Statement.class);
                        isConditionalExpression = (ifStatement instanceof ConditionalExpression);
                        MethodInvocation booleanVariableInitializerMethod = (MethodInvocation) booleanVariableInitializer;
                        handleBothDirectCondition(ifStatement,
                                booleanVariableInitializerMethod, true);
                    }
                }
            }
        }
    }

    private void handleBothDirectCondition(ASTNode ifStatement,
            MethodInvocation method, boolean isDirectConditional) {
        if (setBindingAndCheckMethodName(method, "isEmpty")) {
            if (ifStatement instanceof IfStatement) {
                Statement thenStatement = ((IfStatement) ifStatement)
                        .getThenStatement();
                handleBodyStatements(isDirectConditional, thenStatement);
            } else if (ifStatement instanceof WhileStatement) {
                Statement thenStatement = ((WhileStatement) ifStatement)
                        .getBody();
                handleBodyStatements(isDirectConditional, thenStatement);
            } else if (ifStatement instanceof ForStatement) {
                Statement thenStatement = ((ForStatement) ifStatement)
                        .getBody();
                handleBodyStatements(isDirectConditional, thenStatement);
            } else {
                List stmts = new LinkedList();
                stmts.add(ifStatement);
                handleStatementsInThenClause(stmts, isDirectConditional);
            }
        }
    }

    private void handleBodyStatements(boolean isDirectConditional,
            Statement thenStatement) {
        if (thenStatement instanceof Block) {
            handleStatementsInThenClause(((Block) thenStatement).statements(),
                    isDirectConditional);
        } else if (thenStatement instanceof ExpressionStatement
                || thenStatement instanceof ReturnStatement) {
            List stmts = new LinkedList();
            stmts.add(thenStatement);
            handleStatementsInThenClause(stmts, isDirectConditional);
        }
    }

    private void handle0Conditional(ASTNode ifStatement,
            MethodInvocation initializerMethod, boolean isDirectConditional) {
        if (setBindingAndCheckMethodName(initializerMethod, "size")) {
            if (ifStatement instanceof IfStatement) {
                Statement thenStatement = ((IfStatement) ifStatement)
                        .getThenStatement();
                handleBodyStatements(isDirectConditional, thenStatement);
            } else if (ifStatement instanceof WhileStatement) {
                Statement thenStatement = ((WhileStatement) ifStatement)
                        .getBody();
                handleBodyStatements(isDirectConditional, thenStatement);
            } else if (ifStatement instanceof ForStatement) {
                Statement thenStatement = ((ForStatement) ifStatement)
                        .getBody();
                handleBodyStatements(isDirectConditional, thenStatement);
            } else {
                List stmts = new LinkedList();
                stmts.add(ifStatement);
                handleStatementsInThenClause(stmts, isDirectConditional);
            }
        }
    }

    private void handleStatementsInThenClause(List blockStatements,
            boolean isVConditional) {
        ASTNode statement = null;
        List<MethodInvocation> invoc = new LinkedList<MethodInvocation>();

        for (Object object : blockStatements) {
            statement = (ASTNode) object;
            if (statement instanceof VariableDeclarationStatement) {
                VariableDeclarationStatement stmt = (VariableDeclarationStatement) statement;
                for (Object frag : stmt.fragments()) {
                    VariableDeclarationFragment f = (VariableDeclarationFragment) frag;
                    if (f.getInitializer() instanceof MethodInvocation)
                        invoc.add((MethodInvocation) f.getInitializer());
                }
            } else if (statement instanceof ExpressionStatement) {
                Expression e = ((ExpressionStatement) statement)
                        .getExpression();
                if (e instanceof Assignment) {
                    e = ((Assignment) e).getRightHandSide();
                }
                if (e instanceof MethodInvocation)
                    invoc.add((MethodInvocation) e);
            } else if (statement instanceof ReturnStatement) {
                Expression e = ((ReturnStatement) statement).getExpression();
                if (e instanceof MethodInvocation)
                    invoc.add((MethodInvocation) e);
            } else if (statement instanceof ConditionalExpression) {
                Expression e = ((ConditionalExpression) statement)
                        .getThenExpression();
                if (e instanceof MethodInvocation)
                    invoc.add((MethodInvocation) e);
            }
        }

        for (MethodInvocation invocationOfPoll : invoc) {
            CompilationUnit unit = (CompilationUnit) invocationOfPoll.getRoot();
            Object[] className = Results.getClassNameAndLine(unit,
                    invocationOfPoll);
            boolean hasSync = BindingUtils.hasSynchronized(invocationOfPoll);
            if (checkMethodNameAndBinding(invocationOfPoll)) {
                String mName = invocationOfPoll.getName().getIdentifier();
                if (isVConditional) {
                    results.add(new Result(
                            "boolean = !isEmpty()/int = size(); if/while(boolean/int > 0) { ..."
                                    + mName + ";... } ", (String) className[0],
                            (String) className[1], (IFile) className[2],
                            hasSync));
                    invokeRefactoring(ISEMPTY_FIX, invocationOfPoll);
                } else {
                    results.add(new Result(
                            "if/while(size()>0 or !isEmpty()) { ..." + mName
                                    + ";... } ", (String) className[0],
                            (String) className[1], (IFile) className[2],
                            hasSync));
                    invokeRefactoring(ISEMPTY_FIX, invocationOfPoll);
                }
            }
        }
    }

    private boolean setBindingAndCheckMethodName(
            MethodInvocation methodInvocation, String methodName) {
        fFieldBinding = BindingUtils.resolveBinding(methodInvocation
                .getExpression());
        if (fFieldBinding instanceof IVariableBinding
                && ((IVariableBinding) fFieldBinding).getType() != null) {
            String className = ((IVariableBinding) fFieldBinding).getType()
                    .getName();
            if (BindingUtils.checkQueueType(className, fFieldBinding, info)) {
                return methodInvocation.getName().getIdentifier()
                        .equals(methodName);
            }
        }
        return false;
    }

    protected boolean checkMethodNameAndBinding(
            MethodInvocation methodInvocation) {
        return BindingUtils.considerBinding(
                BindingUtils.resolveBinding(methodInvocation.getExpression()),
                fFieldBinding)
                && methodName.contains(methodInvocation.getName()
                        .getIdentifier());
    }

    public Set<Result> getResults() {
        return results;
    }

    protected Set<Expression> seperateInfixExpression(
            InfixExpression ifExpression) {
        Set<Expression> set = new HashSet<Expression>();
        Expression leftOperand = ifExpression.getLeftOperand();
        Expression rightOperand = ifExpression.getRightOperand();

        while (leftOperand instanceof ParenthesizedExpression) {
            leftOperand = ((ParenthesizedExpression) leftOperand)
                    .getExpression();
        }

        while (rightOperand instanceof ParenthesizedExpression) {
            rightOperand = ((ParenthesizedExpression) rightOperand)
                    .getExpression();
        }

        if (leftOperand instanceof InfixExpression) {
            Expression e = ((InfixExpression) leftOperand).getRightOperand();
            if (e instanceof NumberLiteral
                    && ((NumberLiteral) e).getToken().equals("0"))
                set.add(leftOperand);
            else {
                set.addAll(seperateInfixExpression((InfixExpression) leftOperand));
            }
        } else {
            set.add(ifExpression);
            set.add(leftOperand);
        }

        if (rightOperand instanceof InfixExpression) {
            Expression e = ((InfixExpression) rightOperand).getRightOperand();
            if (e instanceof NumberLiteral
                    && ((NumberLiteral) e).getToken().equals("0"))
                set.add(rightOperand);
            else {
                set.addAll(seperateInfixExpression((InfixExpression) rightOperand));
            }
        } else {
            set.add(ifExpression);
            set.add(rightOperand);
        }

        return set;
    }

    private void doRefactoring(MethodInvocation invocPoll) {
        PatternDetectionAction.isRewrite = true;
        new RefactoringUtil(rewriter, info, fFieldBinding).refactoringIfStmt(
                tmpIfWhileStmt, invocPoll, tmpCondition,
                tmpIfWhileStmt.getAST(), false);
        if (tmpVDecl != null)
            rewriter.remove(tmpVDecl, null);
    }

    private void invokeRefactoring(int refactoringType,
            MethodInvocation invocPoll) {
        if (rewriter == null)
            return;

        switch (refactoringType) {
        case ISEMPTY_FIX:
            if (!isConditionalExpression)
                doRefactoring(invocPoll);
            break;
        default:
            break;
        }
    }
}
