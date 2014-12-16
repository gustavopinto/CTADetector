package br.ufpe.cin.concurrency.forkjoinpatterns.detectors;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Set;

import org.eclipse.core.resources.IFile;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ExpressionStatement;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.IfStatement;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.ParenthesizedExpression;
import org.eclipse.jdt.core.dom.PrefixExpression;
import org.eclipse.jdt.core.dom.ReturnStatement;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.WhileStatement;
import org.eclipse.jdt.internal.corext.dom.ASTNodes;

import br.ufpe.cin.concurrency.forkjoinpatterns.util.BindingUtils;
import br.ufpe.cin.concurrency.forkjoinpatterns.util.PrintUtils;
import br.ufpe.cin.concurrency.forkjoinpatterns.util.PrintableString;
import br.ufpe.cin.concurrency.forkjoinpatterns.util.StatementVisitor;

public class AtomicityViolationPatternForCopyOnWrite extends ASTVisitor {
    protected CollectVariableInfo info;
    private Set<PrintableString> results = new HashSet<PrintableString>();
    private IBinding fFieldBinding;

    public AtomicityViolationPatternForCopyOnWrite(CollectVariableInfo visitor) {
        info = visitor;
    }

    @Override
    public boolean visit(WhileStatement whileStatement) {
        Expression whileExpression = whileStatement.getExpression();
        while (whileExpression instanceof ParenthesizedExpression) {
            whileExpression = ((ParenthesizedExpression) whileExpression)
                    .getExpression();
        }

        if (whileExpression instanceof PrefixExpression) {
            if (((PrefixExpression) whileExpression).getOperator().equals(
                    PrefixExpression.Operator.NOT)) {
                PrefixExpression negationExpression = (PrefixExpression) whileExpression;
                whileExpression = negationExpression.getOperand();
                if (whileExpression instanceof MethodInvocation) {
                    MethodInvocation method = (MethodInvocation) whileExpression;
                    if (setBindingAndCheckMethodName(method, "isEmpty")) {
                        Statement body = whileStatement.getBody();
                        List stmts = null;
                        if (body instanceof Block) {
                            stmts = ((Block) body).statements();
                        } else {
                            stmts = new LinkedList();
                            stmts.add(body);
                        }
                        for (Object object : stmts) {
                            ASTNode statement = (ASTNode) object;
                            ASTNode invocation = null;
                            if (statement instanceof VariableDeclarationFragment) {
                                invocation = ((VariableDeclarationFragment) statement)
                                        .getInitializer();
                            } else if (statement instanceof ExpressionStatement) {
                                invocation = ((ExpressionStatement) statement)
                                        .getExpression();
                            }
                            if (invocation instanceof Assignment) {
                                invocation = ((Assignment) invocation)
                                        .getRightHandSide();
                            }

                            if (invocation instanceof MethodInvocation) {
                                CompilationUnit unit = (CompilationUnit) invocation
                                        .getRoot();
                                Object[] className = PrintUtils
                                        .getClassNameAndLine(unit, invocation);
                                if (checkMethodNameAndBinding(
                                        (MethodInvocation) invocation, "remove")) {
                                    results.add(new PrintableString(
                                            "while(list.isEmpty()) { ... list.remove... }",
                                            (String) className[0],
                                            (String) className[1],
                                            (IFile) className[2],
                                            BindingUtils
                                                    .hasSynchronized(invocation)));
                                }
                            }
                        }
                    }
                }
            }
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

        handleDirectConditional(ifStatement, ifExpression);
        return true;
    }

    protected void handleDirectConditional(IfStatement ifStatement,
            Expression ifExpression) {

        boolean expressionIsNegation = false;

        // Remove the prefix but record its presence.
        if (ifExpression instanceof PrefixExpression) {
            if (((PrefixExpression) ifExpression).getOperator().equals(
                    PrefixExpression.Operator.NOT)) {
                PrefixExpression negationExpression = (PrefixExpression) ifExpression;
                ifExpression = negationExpression.getOperand();
                expressionIsNegation = true;
            }
        }

        // Remove the parentheses.
        while (ifExpression instanceof ParenthesizedExpression) {
            ifExpression = ((ParenthesizedExpression) ifExpression)
                    .getExpression();
        }

        // if(something) or if(!something)
        if (ifExpression instanceof MethodInvocation) {
            // if(method()) or if(!method())
            handleMethodDirectConditional(ifStatement, ifExpression,
                    expressionIsNegation);
        } else if (ifExpression instanceof SimpleName) {
            // if(variable) or if(!variable)
            handleVariableDirectConditional(ifStatement, ifExpression,
                    expressionIsNegation);
        }
    }

    private void handleVariableDirectConditional(IfStatement ifStatement,
            Expression operand, boolean hasNegationPrefix) {
        SimpleName booleanVariableCheck = (SimpleName) operand;

        // if(boolean) or if(!boolean)
        ASTNode booleanVariableInitializer = BindingUtils.getInitializer(
                ifStatement, booleanVariableCheck.resolveBinding(),
                booleanVariableCheck.getFullyQualifiedName(), info);
        if (booleanVariableInitializer instanceof MethodInvocation) {
            MethodInvocation booleanVariableInitializerMethod = (MethodInvocation) booleanVariableInitializer;
            handleBothDirectCondition(ifStatement, hasNegationPrefix,
                    booleanVariableInitializerMethod);
        }
    }

    private void handleMethodDirectConditional(IfStatement ifStatement,
            Expression operand, boolean hasNegationPrefix) {
        // if(method()) or if(!method())

        MethodInvocation listInvocation;
        listInvocation = (MethodInvocation) operand;

        handleBothDirectCondition(ifStatement, hasNegationPrefix,
                listInvocation);
    }

    private void handleBothDirectCondition(IfStatement ifStatement,
            boolean hasNegationPrefix, MethodInvocation method) {
        if (setBindingAndCheckMethodName(method, "contains")) {
            ASTNode elem = (ASTNode) method.arguments().get(0);
            handleContains(ifStatement, hasNegationPrefix, elem);
        } else if (setBindingAndCheckMethodName(method, "isEmpty")) {
            boolean hasReturn = false;
            Statement thenStatement = ifStatement.getThenStatement();
            if (thenStatement instanceof ReturnStatement)
                hasReturn = true;
            else if (thenStatement instanceof Block) {
                for (Object o : ((Block) thenStatement).statements())
                    if (o instanceof ReturnStatement) {
                        hasReturn = true;
                        break;
                    }
            }
            handleIsEmpty(ifStatement, hasNegationPrefix, hasReturn);
        }
    }

    private void handleIsEmpty(IfStatement ifStatement,
            boolean hasNegationPrefix, boolean hasReturn) {
        if (hasReturn) {
            Block parentBlock = (Block) ASTNodes.getParent(ifStatement,
                    Block.class);
            if (parentBlock != null) {
                List stmts = parentBlock.statements();
                List consideredStmts = new LinkedList();
                int index = stmts.indexOf(ifStatement);
                if (index >= 0) {
                    ListIterator it = stmts.listIterator(index + 1);
                    while (it.hasNext()) {
                        Statement stmt = (Statement) it.next();
                        consideredStmts.add(stmt);
                    }
                }
                Statement elseStmt = ifStatement.getElseStatement();
                if (elseStmt instanceof Block) {
                    consideredStmts.addAll(((Block) elseStmt).statements());
                } else if (elseStmt != null)
                    consideredStmts.add(elseStmt);

                for (Object object : consideredStmts) {
                    ASTNode statement = (ASTNode) object;
                    ASTNode invocation = null;
                    if (statement instanceof ReturnStatement) {
                        invocation = ((ReturnStatement) statement)
                                .getExpression();
                    } else if (statement instanceof VariableDeclarationFragment) {
                        invocation = ((VariableDeclarationFragment) statement)
                                .getInitializer();
                    } else if (statement instanceof ExpressionStatement) {
                        invocation = ((ExpressionStatement) statement)
                                .getExpression();
                    }
                    if (invocation instanceof Assignment) {
                        invocation = ((Assignment) invocation)
                                .getRightHandSide();
                    }

                    if (invocation instanceof MethodInvocation) {
                        Expression e = ((MethodInvocation) invocation)
                                .getExpression();
                        while (e instanceof MethodInvocation) {
                            invocation = e;
                            e = ((MethodInvocation) invocation).getExpression();
                        }

                        CompilationUnit unit = (CompilationUnit) invocation
                                .getRoot();
                        Object[] className = PrintUtils.getClassNameAndLine(
                                unit, invocation);
                        if (checkMethodNameAndBinding(
                                (MethodInvocation) invocation, "remove")
                                || checkMethodNameAndBinding(
                                        (MethodInvocation) invocation,
                                        "removeAll")
                                || checkMethodNameAndBinding(
                                        (MethodInvocation) invocation, "get")) {
                            results.add(new PrintableString(
                                    "if(list.isEmpty()) { ... return; } list.remove/get/removeAll;",
                                    (String) className[0],
                                    (String) className[1],
                                    (IFile) className[2], BindingUtils
                                            .hasSynchronized(invocation)));
                        }
                    }
                }
            }
        }
    }

    private void handleContains(IfStatement ifStatement,
            boolean hasNegationPrefix, ASTNode elem) {
        Statement thenStatement = ifStatement.getThenStatement();

        if (thenStatement instanceof Block) {
            List blockStatements = new LinkedList();
            thenStatement.accept(new StatementVisitor(blockStatements));
            handleStatementsInThenClause(ifStatement, elem, blockStatements,
                    hasNegationPrefix, true);
        } else if (thenStatement instanceof ExpressionStatement
                || thenStatement instanceof ReturnStatement) {
            List stmts = new LinkedList();
            stmts.add(thenStatement);
            handleStatementsInThenClause(ifStatement, elem, stmts,
                    hasNegationPrefix, true);
        }

        Statement elseStatement = ifStatement.getElseStatement();
        if (elseStatement instanceof Block) {
            List blockStatements = new LinkedList();
            elseStatement.accept(new StatementVisitor(blockStatements));
            handleStatementsInThenClause(ifStatement, elem, blockStatements,
                    !hasNegationPrefix, true);
        } else if (elseStatement instanceof ExpressionStatement
                || thenStatement instanceof ReturnStatement) {
            List stmts = new LinkedList();
            stmts.add(elseStatement);
            handleStatementsInThenClause(ifStatement, elem, stmts,
                    !hasNegationPrefix, true);
        }
    }

    private void handleStatementsInThenClause(IfStatement ifStatement,
            ASTNode elem, List blockStatements, boolean isNegationCondition,
            boolean isDirectConditional) {
        boolean hasReturn = collectResult(ifStatement, elem, blockStatements,
                isNegationCondition);
        if (hasReturn) {
            Block parentBlock = (Block) ASTNodes.getParent(ifStatement,
                    Block.class);
            if (parentBlock != null) {
                List stmts = parentBlock.statements();
                List consideredStmts = new LinkedList();
                int index = stmts.indexOf(ifStatement);
                if (index >= 0) {
                    ListIterator it = stmts.listIterator(index + 1);
                    while (it.hasNext()) {
                        Statement stmt = (Statement) it.next();
                        consideredStmts.add(stmt);
                        stmt.accept(new StatementVisitor(consideredStmts));
                    }
                    collectResult(ifStatement, elem, consideredStmts,
                            !isNegationCondition);
                }
            }
        }
    }

    protected boolean collectResult(IfStatement ifStmt, ASTNode elem,
            List blockStatements, boolean isNegationCondition) {
        Expression invocation = null;
        ASTNode statement = null;
        boolean hasReturn = false;

        for (Object object : blockStatements) {
            statement = (ASTNode) object;
            if (statement instanceof ReturnStatement)
                hasReturn = true;
            else if (statement instanceof VariableDeclarationFragment) {
                invocation = ((VariableDeclarationFragment) statement)
                        .getInitializer();
            } else if (statement instanceof ExpressionStatement) {
                invocation = ((ExpressionStatement) statement).getExpression();
            }
            if (invocation instanceof Assignment) {
                // value = map.put(key, oldValue)
                invocation = ((Assignment) invocation).getRightHandSide();
            }

            if (invocation instanceof MethodInvocation) {
                CompilationUnit unit = (CompilationUnit) invocation.getRoot();
                Object[] className = PrintUtils.getClassNameAndLine(unit,
                        invocation);
                boolean hasSync = BindingUtils.hasSynchronized(invocation);
                if (checkMethodNameAndBinding((MethodInvocation) invocation,
                        "add")
                        && BindingUtils.checkArgument(
                                (MethodInvocation) invocation, elem)) {
                    if (isNegationCondition) {
                        // boolean = contains(); if(!boolean) { ...
                        // add(); ... }
                        // -> addIfAbsent();
                        results.add(new PrintableString(
                                "boolean = contains(e); if(!boolean or !method()) { ...add(e);... }",
                                (String) className[0], (String) className[1],
                                (IFile) className[2], hasSync));
                    }
                } else if (checkMethodNameAndBinding(
                        (MethodInvocation) invocation, "remove")
                        && BindingUtils.checkArgument(
                                (MethodInvocation) invocation, elem)) {
                    if (!isNegationCondition) {
                        // boolean = containsKey(); if(boolean) { ...
                        // remove(); ... } ->
                        // remove();
                        results.add(new PrintableString(
                                "boolean = contains(e); if(boolean) { ... remove(e); ... } ",
                                (String) className[0], (String) className[1],
                                (IFile) className[2], hasSync));
                    }
                }
            }
        }
        return hasReturn;
    }

    private boolean checkMethodNameAndBinding(
            MethodInvocation methodInvocation, String methodName) {
        return BindingUtils.considerBinding(
                BindingUtils.resolveBinding(methodInvocation.getExpression()),
                fFieldBinding)
                && methodInvocation.getName().getIdentifier()
                        .equals(methodName);
    }

    private boolean setBindingAndCheckMethodName(
            MethodInvocation methodInvocation, String methodName) {
        fFieldBinding = BindingUtils.resolveBinding(methodInvocation
                .getExpression());
        if (fFieldBinding instanceof IVariableBinding
                && ((IVariableBinding) fFieldBinding).getType() != null) {
            String className = ((IVariableBinding) fFieldBinding).getType()
                    .getName();
            if (BindingUtils.checkCopyOnWriteType(className, fFieldBinding,
                    info)) {
                return methodInvocation.getName().getIdentifier()
                        .equals(methodName);
            }
        }
        return false;
    }

    public Set<PrintableString> getResults() {
        return results;
    }
}
