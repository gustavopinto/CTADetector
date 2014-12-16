package br.ufpe.cin.concurrency.fjdetector.detectors;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Set;

import org.eclipse.core.resources.IFile;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.dom.AST;
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
import org.eclipse.jdt.core.dom.InfixExpression;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.NullLiteral;
import org.eclipse.jdt.core.dom.ParenthesizedExpression;
import org.eclipse.jdt.core.dom.ReturnStatement;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.internal.corext.dom.ASTNodes;

import br.ufpe.cin.concurrency.fjdetector.actions.PatternDetectionAction;
import br.ufpe.cin.concurrency.fjdetector.util.BindingUtils;
import br.ufpe.cin.concurrency.fjdetector.util.RefactoringUtil;
import br.ufpe.cin.concurrency.fjdetector.util.Result;
import br.ufpe.cin.concurrency.fjdetector.util.Results;

public class AtomicViolationPatternForConcurrentHashMap extends
        SemanticPatternForConcurrentHashMap {
    private Set<Result> avResults = new HashSet<Result>();

    final static protected int PUTIFABSENT_FIX = 3;
    final static protected int GET_FIX = 4;

    public AtomicViolationPatternForConcurrentHashMap(
            CollectVariableInfo visitor, ICompilationUnit u, ASTRewrite rw) {
        super(visitor, u, rw);
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

    @Override
    public boolean visit(Block b) {
        List stmts = b.statements();
        IBinding vBinding = null;
        ASTNode hashMapKey = null;
        MethodInvocation invocationOfPut = null;
        for (Object o : stmts) {
            Statement s = (Statement) o;
            if (s instanceof ExpressionStatement) {
                Expression e = ((ExpressionStatement) s).getExpression();
                if (e instanceof Assignment) {
                    e = ((Assignment) e).getRightHandSide();
                }
                if (e instanceof MethodInvocation) {
                    MethodInvocation invoc = (MethodInvocation) e;
                    if (invoc.getName().getIdentifier().equals("putIfAbsent")) {
                        IBinding tmpBinding = BindingUtils.resolveBinding(invoc
                                .getExpression());
                        if (tmpBinding instanceof IVariableBinding
                                && ((IVariableBinding) tmpBinding).getType() != null) {
                            String name = ((IVariableBinding) tmpBinding)
                                    .getType().getName();
                            if (BindingUtils.checkMapType(name, tmpBinding,
                                    info)) {
                                vBinding = tmpBinding;
                                hashMapKey = (ASTNode) invoc.arguments().get(0);
                                invocationOfPut = invoc;
                            }
                        }
                    }
                }
            } else if (s instanceof ReturnStatement) {
                Expression e = ((ReturnStatement) s).getExpression();
                if (e instanceof MethodInvocation) {
                    MethodInvocation invoc = (MethodInvocation) e;
                    IBinding tmpBinding = BindingUtils.resolveBinding(invoc
                            .getExpression());
                    if (BindingUtils.considerBinding(tmpBinding, vBinding)
                            && invoc.getName().getIdentifier().equals("get")
                            && BindingUtils.checkArgument(invoc, hashMapKey)) {
                        CompilationUnit unit = (CompilationUnit) invoc
                                .getRoot();
                        Object[] className = Results.getClassNameAndLine(
                                unit, invoc);
                        avResults.add(new Result(
                                "putIfAbsent(k, v); ...; return get(k) ",
                                (String) className[0], (String) className[1],
                                (IFile) className[2], BindingUtils
                                        .hasSynchronized(invoc)));
                        String location = (String) className[0] + className[1];
                        buggyLocations.add(location);
                        invokeRefactoring(PUTIFABSENT_FIX, invocationOfPut,
                                location, invoc, null);
                        invocationOfPut = null;
                    }
                }
            }
        }
        return true;
    }

    @Override
    protected boolean collectResult(IfStatement ifStatement,
            ASTNode hashMapKey, List blockStatements,
            boolean isNegationCondition, boolean isDirectConditional,
            MethodInvocation invocGet) {
        Expression invocationOfPut = null;
        ASTNode statement = null;
        ASTNode tmpV = null;
        boolean avFlag = false;
        boolean hasReturn = false;

        for (Object object : blockStatements) {
            statement = (ASTNode) object;
            if (statement instanceof ReturnStatement)
                hasReturn = true;
            if (statement instanceof VariableDeclarationFragment) {
                invocationOfPut = ((VariableDeclarationFragment) statement)
                        .getInitializer();
                if (invocationOfPut instanceof MethodInvocation
                        && checkMethodNameAndBinding(
                                (MethodInvocation) invocationOfPut,
                                "putIfAbsent")
                        && BindingUtils.checkArgument(
                                (MethodInvocation) invocationOfPut, hashMapKey)) {
                    tmpV = ((VariableDeclarationFragment) statement).getName();
                    avFlag = checkAssignment(((Block) ASTNodes.getParent(
                            statement, Block.class)).statements(),
                            invocationOfPut, tmpV, object);
                }
            } else if (statement instanceof ExpressionStatement) {
                invocationOfPut = ((ExpressionStatement) statement)
                        .getExpression();
            }
            if (invocationOfPut instanceof Assignment) {
                // value = map.putIfAbsent(key, oldValue)
                Assignment assign = (Assignment) invocationOfPut;
                invocationOfPut = assign.getRightHandSide();
                if (invocationOfPut instanceof MethodInvocation
                        && checkMethodNameAndBinding(
                                (MethodInvocation) invocationOfPut,
                                "putIfAbsent")) {
                    tmpV = assign.getLeftHandSide();
                    avFlag = checkAssignment(((Block) ASTNodes.getParent(
                            statement, Block.class)).statements(),
                            invocationOfPut, tmpV, object);
                }
            }

            if (!avFlag && invocationOfPut instanceof MethodInvocation) {
                CompilationUnit unit = (CompilationUnit) invocationOfPut
                        .getRoot();
                Object[] className = Results.getClassNameAndLine(unit,
                        invocationOfPut);
                boolean hasSync = BindingUtils.hasSynchronized(invocationOfPut);
                if (checkMethodNameAndBinding(
                        (MethodInvocation) invocationOfPut, "putIfAbsent")
                        && BindingUtils.checkArgument(
                                (MethodInvocation) invocationOfPut, hashMapKey)) {
                    ASTNode arg2 = (ASTNode) ((MethodInvocation) invocationOfPut)
                            .arguments().get(1);

                    Block innerBlock = (Block) ASTNodes.getParent(
                            invocationOfPut, Block.class);
                    Block outterBlock = (Block) ASTNodes.getParent(ifStatement,
                            Block.class);
                    List stmtsList = new LinkedList();

                    int outterIndex = outterBlock.statements().indexOf(
                            ifStatement) + 1;
                    if (outterIndex > 0
                            && outterIndex < outterBlock.statements().size()) {
                        ListIterator stmtsIt = outterBlock.statements()
                                .listIterator(outterIndex);
                        while (stmtsIt.hasNext()) {
                            stmtsList.add(stmtsIt.next());
                        }
                    }

                    if (ASTNodes.isParent(innerBlock, outterBlock)) {
                        int innerIndex = innerBlock.statements().indexOf(
                                invocationOfPut) + 1;
                        if (innerIndex > 0
                                && innerIndex < innerBlock.statements().size()) {
                            ListIterator stmtsIt = innerBlock.statements()
                                    .listIterator(innerIndex);
                            while (stmtsIt.hasNext()) {
                                stmtsList.add(stmtsIt.next());
                            }
                        }
                    }

                    for (Object o : stmtsList) {
                        Statement stmt = (Statement) o;
                        if (stmt instanceof ReturnStatement) {
                            ReturnStatement rstmt = (ReturnStatement) stmt;
                            Expression re = rstmt.getExpression();
                            if (re instanceof MethodInvocation) {
                                if (checkMethodNameAndBinding(
                                        (MethodInvocation) re, "get")
                                        && BindingUtils.checkArgument(
                                                (MethodInvocation) re,
                                                hashMapKey)) {
                                    avResults
                                            .add(new Result(
                                                    "if(method() or variable){putIfAbsent(k, v); ...} return get(k) ",
                                                    (String) className[0],
                                                    (String) className[1],
                                                    (IFile) className[2],
                                                    hasSync));
                                    String location = (String) className[0]
                                            + className[1];
                                    buggyLocations.add(location);
                                    invokeRefactoring(PUTIFABSENT_FIX,
                                            invocationOfPut, location,
                                            (MethodInvocation) re, null);
                                } else if (BindingUtils
                                        .compareArguments((Expression) arg2,
                                                ((MethodInvocation) re)
                                                        .getExpression())) {
                                    avResults
                                            .add(new Result(
                                                    "if(method() or variable){putIfAbsent(k, v); ...} return v.m() ",
                                                    (String) className[0],
                                                    (String) className[1],
                                                    (IFile) className[2],
                                                    hasSync));
                                    String location = (String) className[0]
                                            + className[1];
                                    buggyLocations.add(location);
                                    invokeRefactoring(PUTIFABSENT_FIX,
                                            invocationOfPut, location, null,
                                            null);
                                }
                            } else if (BindingUtils.compareArguments(
                                    (Expression) arg2, re)) {
                                avResults
                                        .add(new Result(
                                                "if(method() or variable){v = ...; putIfAbsent(k, v); ...} return v; ",
                                                (String) className[0],
                                                (String) className[1],
                                                (IFile) className[2], hasSync));
                                String location = (String) className[0]
                                        + className[1];
                                buggyLocations.add(location);
                                invokeRefactoring(PUTIFABSENT_FIX,
                                        invocationOfPut, location, null, null);
                            }
                        } else {
                            List<MethodInvocation> list = new LinkedList<MethodInvocation>();
                            stmt.accept(new GetMethodInvoc(list));
                            for (MethodInvocation invoc : list) {
                                if (checkMethodNameAndBinding(invoc, "get")
                                        && BindingUtils.checkArgument(invoc,
                                                hashMapKey)) {
                                    avResults
                                            .add(new Result(
                                                    "if(method() or variable){v = ...; putIfAbsent(k, v); ...} get(k); ",
                                                    (String) className[0],
                                                    (String) className[1],
                                                    (IFile) className[2],
                                                    hasSync));
                                    String location = (String) className[0]
                                            + className[1];
                                    buggyLocations.add(location);
                                    invokeRefactoring(PUTIFABSENT_FIX,
                                            invocationOfPut, location, invoc,
                                            null);
                                } else {
                                    Expression e = invoc.getExpression();
                                    while (e instanceof MethodInvocation) {
                                        e = ((MethodInvocation) e)
                                                .getExpression();
                                    }
                                    if (e != null
                                            && BindingUtils.compareArguments(e,
                                                    (Expression) arg2)) {
                                        avResults
                                                .add(new Result(
                                                        "if(method() or variable){v = ...; putIfAbsent(k, v); ...} v.m(); ",
                                                        (String) className[0],
                                                        (String) className[1],
                                                        (IFile) className[2],
                                                        hasSync));
                                        String location = (String) className[0]
                                                + className[1];
                                        buggyLocations.add(location);
                                        invokeRefactoring(PUTIFABSENT_FIX,
                                                invocationOfPut, location,
                                                null, null);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        return hasReturn;
    }

    private boolean checkAssignment(List blockStatements,
            Expression invocationOfPut, ASTNode tmpV, Object object) {
        ASTNode arg2 = (ASTNode) ((MethodInvocation) invocationOfPut)
                .arguments().get(1);
        ListIterator iter = blockStatements.listIterator(blockStatements
                .indexOf(object) + 1);
        while (iter.hasNext()) {
            ASTNode node = (ASTNode) iter.next();
            if (node instanceof IfStatement) {
                IfStatement ifstmt = (IfStatement) node;
                Expression ife = ifstmt.getExpression();
                if (ife instanceof InfixExpression) {
                    Expression leftOperand = ((InfixExpression) ife)
                            .getLeftOperand();
                    Expression rightOperand = ((InfixExpression) ife)
                            .getRightOperand();
                    InfixExpression.Operator op = ((InfixExpression) ife)
                            .getOperator();
                    if (BindingUtils.compareArguments(leftOperand,
                            (Expression) tmpV)
                            && rightOperand instanceof NullLiteral) {
                        if (op.equals(InfixExpression.Operator.NOT_EQUALS)) {
                            Statement thenStmt = ifstmt.getThenStatement();
                            return checkOldV2NewV(tmpV, arg2, thenStmt);
                        } else if (op.equals(InfixExpression.Operator.EQUALS)) {
                            Statement elseStmt = ifstmt.getElseStatement();
                            return checkOldV2NewV(tmpV, arg2, elseStmt);
                        }
                    }
                }
            }
        }
        return false;
    }

    private boolean checkOldV2NewV(ASTNode tmpV, ASTNode arg2,
            Statement thenStmt) {
        List list = new LinkedList();
        if (thenStmt instanceof Block) {
            list.addAll(((Block) thenStmt).statements());
        } else if (thenStmt instanceof ExpressionStatement) {
            list.add(thenStmt);
        }
        for (Object stmtInIf : list) {
            if (stmtInIf instanceof ExpressionStatement) {
                Expression eInIf = ((ExpressionStatement) stmtInIf)
                        .getExpression();
                if (eInIf instanceof Assignment) {
                    Expression left = ((Assignment) eInIf).getLeftHandSide();
                    Expression right = ((Assignment) eInIf).getRightHandSide();
                    if (BindingUtils.compareArguments(left, (Expression) arg2)
                            && BindingUtils.compareArguments(right,
                                    (Expression) tmpV)) {
                        return true;
                    }
                }
            } else if (stmtInIf instanceof ReturnStatement) {
                Expression eInIf = ((ReturnStatement) stmtInIf).getExpression();
                if (BindingUtils.compareArguments(eInIf, (Expression) tmpV))
                    return true;
            }
        }
        return false;
    }

    @Override
    protected void handleGetInContainsKey(IfStatement ifStatement,
            ASTNode hashMapKey, MethodInvocation containsinvoc) {
        Statement thenStatement = ifStatement.getThenStatement();
        CompilationUnit unit = (CompilationUnit) ifStatement.getRoot();
        List stmts = new LinkedList();
        List<MethodInvocation> methodInvocs = new LinkedList<MethodInvocation>();

        if (thenStatement instanceof Block) {
            stmts.addAll(((Block) thenStatement).statements());

        } else if (thenStatement instanceof ExpressionStatement
                || thenStatement instanceof ReturnStatement) {
            stmts.add(thenStatement);
        }

        for (Object o : stmts) {
            if (o instanceof ReturnStatement) {
                ASTNode n = ((ReturnStatement) o).getExpression();
                if (n instanceof Assignment) {
                    n = ((Assignment) n).getRightHandSide();
                }
                if (n instanceof MethodInvocation)
                    methodInvocs.add((MethodInvocation) n);
            } else if (o instanceof ExpressionStatement) {
                ASTNode n = ((ExpressionStatement) o).getExpression();
                if (n instanceof Assignment) {
                    n = ((Assignment) n).getRightHandSide();
                }
                if (n instanceof MethodInvocation)
                    methodInvocs.add((MethodInvocation) n);
            } else if (o instanceof VariableDeclarationStatement) {
                List vFrags = ((VariableDeclarationStatement) o).fragments();
                for (Object frag : vFrags) {
                    Expression e = ((VariableDeclarationFragment) frag)
                            .getInitializer();
                    if (e instanceof MethodInvocation)
                        methodInvocs.add((MethodInvocation) e);
                }
            }

            for (MethodInvocation invoc : methodInvocs) {
                if (checkMethodNameAndBinding(invoc, "get")
                        && BindingUtils.checkArgument(invoc, hashMapKey)) {
                    Object[] className = Results.getClassNameAndLine(unit,
                            invoc);
                    avResults.add(new Result(
                            "if(containsKey(k)){ get(k); ...} ",
                            (String) className[0], (String) className[1],
                            (IFile) className[2], BindingUtils
                                    .hasSynchronized(invoc)));
                    invokeRefactoring(GET_FIX, invoc, (String) className[0]
                            + className[1], containsinvoc, ifStatement);
                }
            }
        }
    }

    public Set<Result> getResults() {
        return avResults;
    }

    private class GetMethodInvoc extends ASTVisitor {
        List<MethodInvocation> list;

        GetMethodInvoc(List<MethodInvocation> l) {
            list = l;
        }

        @Override
        public boolean visit(MethodInvocation invoc) {
            list.add(invoc);
            return true;
        }
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
            if (((InfixExpression) leftOperand).getRightOperand() instanceof NullLiteral)
                set.add(leftOperand);
            else {
                set.addAll(seperateInfixExpression((InfixExpression) leftOperand));
            }
        } else {
            set.add(ifExpression);
            set.add(leftOperand);
        }

        if (rightOperand instanceof InfixExpression) {
            if (((InfixExpression) rightOperand).getRightOperand() instanceof NullLiteral)
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

    private void refactoringForPutIfAbsent(MethodInvocation oldInvoc,
            MethodInvocation returnInvoc) {
        PatternDetectionAction.isRewrite = true;
        AST ast = oldInvoc.getAST();
        MethodInvocation newInvoc = (MethodInvocation) ASTNode.copySubtree(ast,
                oldInvoc);
        String name = refactoringReturnValue(oldInvoc, ast, newInvoc);
        if (returnInvoc != null && name != null) {
            SimpleName returnV = ast.newSimpleName(name);
            rewriter.replace(returnInvoc, returnV, null);
        }
    }

    private void refactoringGet(MethodInvocation oldInvoc, IfStatement ifStmt,
            MethodInvocation containsInvoc) {
        PatternDetectionAction.isRewrite = true;
        new RefactoringUtil(rewriter, info, fFieldBinding).refactoringIfStmt(
                ifStmt, oldInvoc, containsInvoc, ifStmt.getAST(), false);
    }

    private void invokeRefactoring(int refactoringType,
            Expression invocationOfPut, String className,
            MethodInvocation retrunInvoc, IfStatement ifStmt) {
        if (rewriter == null || refactoredLine.contains(className)) {
            return;
        }

        refactoredLine.add(className);
        switch (refactoringType) {
        case PUTIFABSENT_FIX:
            refactoringForPutIfAbsent((MethodInvocation) invocationOfPut,
                    retrunInvoc);
            break;
        case GET_FIX:
            refactoringGet((MethodInvocation) invocationOfPut, ifStmt,
                    retrunInvoc);
            break;
        default:
            break;
        }
    }
}
