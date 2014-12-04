package concurrentpatterns.detection;

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
import org.eclipse.jdt.core.dom.CastExpression;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ExpressionStatement;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.IfStatement;
import org.eclipse.jdt.core.dom.InfixExpression;
import org.eclipse.jdt.core.dom.InfixExpression.Operator;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.NullLiteral;
import org.eclipse.jdt.core.dom.ParameterizedType;
import org.eclipse.jdt.core.dom.ParenthesizedExpression;
import org.eclipse.jdt.core.dom.PrefixExpression;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.ReturnStatement;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SimpleType;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.internal.corext.dom.ASTNodes;
import org.eclipse.ui.console.ConsolePlugin;
import org.eclipse.ui.console.IConsole;
import org.eclipse.ui.console.MessageConsole;
import org.eclipse.ui.console.MessageConsoleStream;

import concurrencypatterns.popup.actions.PatternDetectionAction;
import concurrencypatterns.util.BindingUtils;
import concurrencypatterns.util.PrintUtils;
import concurrencypatterns.util.PrintableString;
import concurrencypatterns.util.RefactoringUtil;
import concurrencypatterns.util.StatementVisitor;

public class SemanticPatternForConcurrentHashMap extends ASTVisitor {

    protected IBinding fFieldBinding;
    private Set<PrintableString> results = new HashSet<PrintableString>();
    protected Set<String> refactoredLine = new HashSet<String>();
    protected CollectVariableInfo info;
    protected ICompilationUnit unit;
    protected ASTRewrite rewriter;

    public Set<String> buggyLocations = new HashSet<String>();

    final static protected int PUT_FIX = 0;
    final static protected int REMOVE_FIX = 1;
    final static protected int REPLACE_FIX = 2;

    public SemanticPatternForConcurrentHashMap(CollectVariableInfo visitor,
            ICompilationUnit u, ASTRewrite rw) {
        info = visitor;
        unit = u;
        rewriter = rw;
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
            // Operator conditional: if(something operator something)
            handleOperatorConditional(ifStatement, ifExpression);
        } else {
            // Direct conditional: if(something)
            handleDirectConditional(ifStatement, ifExpression);
        }

        return true;
    }

    protected void handleOperatorConditional(IfStatement ifStatement,
            Expression ifExpression) {

        Operator operator = ((InfixExpression) ifExpression).getOperator();
        InfixExpression testExpression = (InfixExpression) ifExpression;
        Expression leftOperand = testExpression.getLeftOperand();
        Expression rightOperand = testExpression.getRightOperand();

        // TODO - (cleanup) can factor this out some, but ensure we don't accept
        // other operators
        if (rightOperand instanceof NullLiteral) {
            // if(? operator null)
            // if(? == null)
            // Remove the parentheses.
            while (leftOperand instanceof ParenthesizedExpression) {
                leftOperand = ((ParenthesizedExpression) leftOperand)
                        .getExpression();
            }
            if (leftOperand instanceof MethodInvocation) {
                // if(method() == null)
                handleMethodAndNullConditional(ifStatement, leftOperand,
                        operator);
            } else if (leftOperand instanceof SimpleName
                    || leftOperand instanceof QualifiedName) {
                // if(variable == null)
                handleVariableAndNullConditional(ifStatement, leftOperand,
                        operator);
            } else if (leftOperand instanceof Assignment) {
                // if((variable = method()) == null)
                handleAssignmentAndNullConditional(ifStatement, leftOperand,
                        operator);
            }
        }
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

        // TODO (possible feature, low priority)
        // for the generalization, I think this needs to allow for "get" - might
        // need to restructure,
        // factor out "instanceof MethodInvocation".

        // if(variable) or if(!variable)

        SimpleName booleanVariableCheck = (SimpleName) operand;

        // if(boolean) or if(!boolean)
        ASTNode booleanVariableInitializer = BindingUtils.getInitializer(
                ifStatement, booleanVariableCheck.resolveBinding(),
                booleanVariableCheck.getFullyQualifiedName(), info);
        if (booleanVariableInitializer instanceof MethodInvocation) {
            MethodInvocation booleanVariableInitializerMethod = (MethodInvocation) booleanVariableInitializer;
            Expression booleanVariableInitializerExpression = booleanVariableInitializerMethod
                    .getExpression();
            handleBothDirectCondition(ifStatement, hasNegationPrefix,
                    booleanVariableInitializerMethod,
                    booleanVariableInitializerExpression);
        }
    }

    private void handleMethodDirectConditional(IfStatement ifStatement,
            Expression operand, boolean hasNegationPrefix) {
        // if(method()) or if(!method())

        MethodInvocation mapInvocation;
        mapInvocation = (MethodInvocation) operand;
        Expression mapInvocationExpression = mapInvocation.getExpression();

        // TODO (cleanup) can factor the common operations between these two
        // clauses
        handleBothDirectCondition(ifStatement, hasNegationPrefix,
                mapInvocation, mapInvocationExpression);
    }

    private void handleBothDirectCondition(IfStatement ifStatement,
            boolean hasNegationPrefix, MethodInvocation method,
            Expression expression) {
        if (expression instanceof MethodInvocation) {
            if (setBindingAndCheckMethodName((MethodInvocation) expression,
                    "get") && method.getName().getIdentifier().equals("equals")) {
                // boolean = get().equals();
                // if(boolean) or boolean =
                // get().equals(); if(!boolean)

                ASTNode hashMapKey = (ASTNode) ((MethodInvocation) expression)
                        .arguments().get(0);
                handleGetOrContainskey(ifStatement, hasNegationPrefix,
                        hashMapKey, null);
            }
        } else if (setBindingAndCheckMethodName(method, "containsKey")) {
            // boolean = containsKey(); if(boolean) or boolean = containsKey();
            // if(!boolean)
            // if(containsKey()) method2(); or !containsKey() form
            ASTNode hashMapKey = (ASTNode) method.arguments().get(0);
            handleGetOrContainskey(ifStatement, hasNegationPrefix, hashMapKey,
                    method);
            if (!hasNegationPrefix) {
                handleGetInContainsKey(ifStatement, hashMapKey, method);
            }
        }
    }

    protected void handleGetInContainsKey(IfStatement ifStatement,
            ASTNode hashMapKey, MethodInvocation containsInvoc) {

    }

    private void handleGetOrContainskey(IfStatement ifStatement,
            boolean hasNegationPrefix, ASTNode hashMapKey,
            MethodInvocation method) {
        Statement thenStatement = ifStatement.getThenStatement();

        if (thenStatement instanceof Block) {
            // boolean = get().equals(); if(boolean) { ... method(); } or
            // !boolean form
            // if(method()) { ... method2(); } or !method() form
            // List blockStatements = ((Block) thenStatement).statements();
            List blockStatements = new LinkedList();
            thenStatement.accept(new StatementVisitor(blockStatements));
            handleStatementsInThenClause(ifStatement, hashMapKey,
                    blockStatements, hasNegationPrefix, true, method);
        } else if (thenStatement instanceof ExpressionStatement
                || thenStatement instanceof ReturnStatement) {
            // boolean = get().equals(); if(boolean) method(); or !boolean form
            // if(method()) method2(); or !method() form
            List stmts = new LinkedList();
            stmts.add(thenStatement);
            handleStatementsInThenClause(ifStatement, hashMapKey, stmts,
                    hasNegationPrefix, true, method);
        }

        Statement elseStatement = ifStatement.getElseStatement();
        if (elseStatement instanceof Block) {
            // variable = get(); if(boolean) { ... } else {... method();}
            // or !boolean form
            // List blockStatements = ((Block) elseStatement).statements();
            List blockStatements = new LinkedList();
            elseStatement.accept(new StatementVisitor(blockStatements));
            handleStatementsInThenClause(ifStatement, hashMapKey,
                    blockStatements, !hasNegationPrefix, true, method);
        } else if (elseStatement instanceof ExpressionStatement
                || thenStatement instanceof ReturnStatement) {
            // variable = get(); if(boolean) { ... } else method();
            // or !boolean form
            List stmts = new LinkedList();
            stmts.add(elseStatement);
            handleStatementsInThenClause(ifStatement, hashMapKey, stmts,
                    !hasNegationPrefix, true, method);
        }
    }

    private void handleVariableAndNullConditional(IfStatement ifStatement,
            Expression leftOperand, Operator operator) {
        String name = (leftOperand instanceof SimpleName) ? ((SimpleName) leftOperand)
                .getFullyQualifiedName() : ((QualifiedName) leftOperand)
                .getFullyQualifiedName();
        IBinding binding = BindingUtils.resolveBinding(leftOperand);

        ASTNode nullVariableInitializer = BindingUtils.getInitializer(
                ifStatement, binding, name, info);

        if (nullVariableInitializer instanceof CastExpression)
            nullVariableInitializer = ((CastExpression) nullVariableInitializer)
                    .getExpression();

        if (nullVariableInitializer instanceof MethodInvocation) {
            handleNullConditional(ifStatement, operator,
                    (MethodInvocation) nullVariableInitializer);
        }
    }

    private void handleMethodAndNullConditional(IfStatement ifStatement,
            Expression leftOperand, Operator operator) {
        handleNullConditional(ifStatement, operator,
                (MethodInvocation) leftOperand);
    }

    private void handleAssignmentAndNullConditional(IfStatement ifStatement,
            Expression leftOperand, Operator operator) {
        Assignment assign = (Assignment) leftOperand;
        Expression e = assign.getRightHandSide();
        if (e instanceof MethodInvocation)
            handleNullConditional(ifStatement, operator, (MethodInvocation) e);
    }

    private void handleNullConditional(IfStatement ifStatement,
            Operator operator, MethodInvocation nullInitializerMethod) {
        boolean testNotNull = !operator.equals(InfixExpression.Operator.EQUALS);
        if (setBindingAndCheckMethodName(nullInitializerMethod, "get")) {
            // variable = get(); if(variable == null) or
            // variable = get()); if(variable != null)
            ASTNode hashMapKey = (ASTNode) nullInitializerMethod.arguments()
                    .get(0);
            Statement thenStatement = ifStatement.getThenStatement();
            if (thenStatement instanceof Block) {
                // variable = get(); if(variable ==
                // null) { ... method(); } or !=
                // form
                // List blockStatements = ((Block) thenStatement).statements();
                List blockStatements = new LinkedList();
                thenStatement.accept(new StatementVisitor(blockStatements));
                handleStatementsInThenClause(ifStatement, hashMapKey,
                        blockStatements, testNotNull, false,
                        nullInitializerMethod);
            } else if (thenStatement instanceof ExpressionStatement
                    || thenStatement instanceof ReturnStatement) {
                // variable = get(); if(variable ==
                // null) method(); or != form
                List stmts = new LinkedList();
                stmts.add(thenStatement);
                handleStatementsInThenClause(ifStatement, hashMapKey, stmts,
                        testNotNull, false, nullInitializerMethod);
            }

            Statement elseStatement = ifStatement.getElseStatement();
            if (elseStatement instanceof Block) {
                // variable = get(); if(variable ==
                // null) { ... } else {... method();} or !=
                // form
                // List blockStatements = ((Block) elseStatement).statements();
                List blockStatements = new LinkedList();
                elseStatement.accept(new StatementVisitor(blockStatements));
                handleStatementsInThenClause(ifStatement, hashMapKey,
                        blockStatements, !testNotNull, false,
                        nullInitializerMethod);
            } else if (elseStatement instanceof ExpressionStatement
                    || thenStatement instanceof ReturnStatement) {
                // variable = get(); if(variable ==
                // null) ...; else method(); or != form
                List stmts = new LinkedList();
                stmts.add(elseStatement);
                handleStatementsInThenClause(ifStatement, hashMapKey, stmts,
                        !testNotNull, false, nullInitializerMethod);
            }
        }
    }

    private void handleStatementsInThenClause(IfStatement ifStatement,
            ASTNode hashMapKey, List blockStatements,
            boolean isNegationCondition, boolean isDirectConditional,
            MethodInvocation invocGet) {
        boolean hasReturn = collectResult(ifStatement, hashMapKey,
                blockStatements, isNegationCondition, isDirectConditional,
                invocGet);
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
                    collectResult(ifStatement, hashMapKey, consideredStmts,
                            !isNegationCondition, isDirectConditional, invocGet);
                }
            }
        }
    }

    protected boolean collectResult(IfStatement ifStmt, ASTNode hashMapKey,
            List blockStatements, boolean isNegationCondition,
            boolean isDirectConditional, MethodInvocation invocGet) {
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
                        "put")
                        && BindingUtils.checkArgument(
                                (MethodInvocation) invocation, hashMapKey)) {
                    if (isNegationCondition) {
                        if (isDirectConditional) {
                            // boolean = containsKey(); if(!boolean) { ...
                            // put(); ... }
                            // -> putIfAbsent();
                            results.add(new PrintableString(
                                    "boolean = containsKey()/get(); if(!boolean or !method()) { ...put();... } ",
                                    (String) className[0],
                                    (String) className[1],
                                    (IFile) className[2], hasSync));
                            String location = (String) className[0]
                                    + className[1];
                            buggyLocations.add(location);
                            invokeRefactoring(PUT_FIX, location, null,
                                    (MethodInvocation) invocation, null);
                        } else {
                            // if(method() != null) { ...put();... } ->
                            // replace();
                            results.add(new PrintableString(
                                    "if(method() != null or v != null) { ...put();... } ",
                                    (String) className[0],
                                    (String) className[1],
                                    (IFile) className[2], hasSync));
                            String location = (String) className[0]
                                    + className[1];
                            buggyLocations.add(location);
                            invokeRefactoring(REPLACE_FIX, location, null,
                                    (MethodInvocation) invocation, null);
                        }
                    } else {
                        if (isDirectConditional) {
                            // boolean = containsKey(); if(boolean) {
                            // ...put();... }
                            // -> replace();
                            results.add(new PrintableString(
                                    "boolean = containsKey()/get(); if(boolean or method()) { ...put();... } ",
                                    (String) className[0],
                                    (String) className[1],
                                    (IFile) className[2], hasSync));
                            String location = (String) className[0]
                                    + className[1];
                            buggyLocations.add(location);
                            invokeRefactoring(REPLACE_FIX, location, null,
                                    (MethodInvocation) invocation, null);
                        } else {
                            // if(method() == null) { ...put();... } ->
                            // putIfAbsent();
                            results.add(new PrintableString(
                                    "if(method() == null or v == null) { ...put();... } ",
                                    (String) className[0],
                                    (String) className[1],
                                    (IFile) className[2], hasSync));
                            String location = (String) className[0]
                                    + className[1];
                            buggyLocations.add(location);
                            invokeRefactoring(PUT_FIX, location, null,
                                    (MethodInvocation) invocation, null);
                        }
                    }
                } else if (checkMethodNameAndBinding(
                        (MethodInvocation) invocation, "remove")
                        && BindingUtils.checkArgument(
                                (MethodInvocation) invocation, hashMapKey)) {
                    if (invocation.getParent() instanceof ExpressionStatement) {
                        ExpressionStatement eStmt = (ExpressionStatement) invocation
                                .getParent();
                        if (eStmt.getExpression() == invocation)
                            return hasReturn;
                    }
                    if (!isNegationCondition) {
                        if (isDirectConditional) {
                            // boolean = containsKey(); if(boolean) { ...
                            // remove(); ... } ->
                            // remove();
                            results.add(new PrintableString(
                                    "boolean = containsKey(); if(boolean) { ... remove(); ... } ",
                                    (String) className[0],
                                    (String) className[1],
                                    (IFile) className[2], hasSync));
                            String location = (String) className[0]
                                    + className[1];
                            buggyLocations.add(location);
                            invokeRefactoring(REMOVE_FIX, location, ifStmt,
                                    (MethodInvocation) invocation, invocGet);
                        }
                    } else {
                        if (!isDirectConditional) {
                            // if(method() != null) { ... remove(); ... } ->
                            // remove();
                            results.add(new PrintableString(
                                    "if(method() != null) { ... remove(); ... } ",
                                    (String) className[0],
                                    (String) className[1],
                                    (IFile) className[2], hasSync));
                            String location = (String) className[0]
                                    + className[1];
                            buggyLocations.add(location);
                            invokeRefactoring(REMOVE_FIX, location, ifStmt,
                                    (MethodInvocation) invocation, invocGet);
                        }
                    }
                }
            }
        }
        return hasReturn;
    }

    protected boolean checkMethodNameAndBinding(
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
            if (BindingUtils.checkMapType(className, fFieldBinding, info)) {
                return methodInvocation.getName().getIdentifier()
                        .equals(methodName);
            }
        }
        return false;
    }

    public Set<PrintableString> getResults() {
        return results;
    }

    private void refactoringForPut(MethodInvocation oldInvoc) {
        PatternDetectionAction.isRewrite = true;
        AST ast = oldInvoc.getAST();
        MethodInvocation newInvoc = (MethodInvocation) ASTNode.copySubtree(ast,
                oldInvoc);
        newInvoc.setName(ast.newSimpleName("putIfAbsent"));

        changeMapType(ast);
        refactoringReturnValue(oldInvoc, ast, newInvoc);
    }

    private void changeMapType(AST ast) {
        ASTNode mapDelc = info.mapVariablesForRewrite.get(fFieldBinding
                .getName());
        ASTNode copyDelc = ASTNode.copySubtree(ast, mapDelc);
        if (copyDelc instanceof FieldDeclaration) {
            FieldDeclaration fieldD = (FieldDeclaration) copyDelc;
            Type type = fieldD.getType();
            String typeName = "";
            if (type instanceof SimpleType) {
                typeName = ((SimpleType) type).getName()
                        .getFullyQualifiedName();
                fieldD.setType(ast.newSimpleType(ast
                        .newSimpleName("ConcurrentHashMap")));
            } else if (type instanceof ParameterizedType) {
                typeName = ((ParameterizedType) type).getType().toString();
                ((ParameterizedType) type).setType(ast.newSimpleType(ast
                        .newSimpleName("ConcurrentHashMap")));
            }
            if (!typeName.contains("ConcurrentHashMap")
                    && !typeName.contains("ConcurrentMap"))
                rewriter.replace(mapDelc, fieldD, null);
        } else if (copyDelc instanceof VariableDeclarationStatement) {
            VariableDeclarationStatement varD = (VariableDeclarationStatement) copyDelc;
            Type type = varD.getType();
            String typeName = "";
            if (type instanceof SimpleType) {
                typeName = ((SimpleType) type).getName()
                        .getFullyQualifiedName();
                varD.setType(ast.newSimpleType(ast
                        .newSimpleName("ConcurrentHashMap")));
            } else if (type instanceof ParameterizedType) {
                typeName = ((ParameterizedType) type).getType().toString();
                ((ParameterizedType) type).setType(ast.newSimpleType(ast
                        .newSimpleName("ConcurrentHashMap")));
            }
            if (!typeName.contains("ConcurrentHashMap")
                    && !typeName.contains("ConcurrentMap"))
                rewriter.replace(mapDelc, varD, null);
        }
    }

    protected String refactoringReturnValue(MethodInvocation oldInvoc, AST ast,
            MethodInvocation newInvoc) {
        Object value = oldInvoc.arguments().get(1);
        String putVName = null;
        if (value instanceof Name) {
            String name = ((Name) value).getFullyQualifiedName();
            putVName = name;
            if (name.contains("."))
                name = "Value";
            VariableDeclarationFragment tmpV = ast
                    .newVariableDeclarationFragment();
            tmpV.setName(ast.newSimpleName("tmp" + name));
            tmpV.setInitializer(newInvoc);
            VariableDeclarationStatement tmpVDecl = ast
                    .newVariableDeclarationStatement(tmpV);
            VariableDeclarationFragment frag = info.getVariableMap().get(
                    ((Name) value).resolveBinding());
            ASTNode vDecl = null;
            if (frag != null)
                vDecl = frag.getParent();
            else {
                MethodDeclaration methodDecl = (MethodDeclaration) ASTNodes
                        .getParent(oldInvoc, MethodDeclaration.class);
                List parameters = methodDecl.parameters();
                for (Object o : parameters) {
                    SingleVariableDeclaration param = (SingleVariableDeclaration) o;
                    if (param.getName().getIdentifier()
                            .equals(((Name) value).getFullyQualifiedName())) {
                        vDecl = param;
                        break;
                    }
                }
            }
            if(vDecl == null) return null;
            
            Type vType = null;
            if (vDecl instanceof VariableDeclarationStatement) {
                vType = (Type) ASTNode.copySubtree(ast,
                        ((VariableDeclarationStatement) vDecl).getType());
            } else if (vDecl instanceof FieldDeclaration) {
                vType = (Type) ASTNode.copySubtree(ast,
                        ((FieldDeclaration) vDecl).getType());
            } else if (vDecl instanceof SingleVariableDeclaration) {
                vType = (Type) ASTNode.copySubtree(ast,
                        ((SingleVariableDeclaration) vDecl).getType());
            }
            
            tmpVDecl.setType(vType);

            IfStatement ifStmt = ast.newIfStatement();
            InfixExpression isNullExpression = RefactoringUtil
                    .constructInfixExp(
                            ast,
                            ast.newName(tmpV.getName().getFullyQualifiedName()),
                            ast.newNullLiteral(), Operator.NOT_EQUALS);
            ifStmt.setExpression(isNullExpression);
            Assignment assign = ast.newAssignment();
            assign.setLeftHandSide(ast.newName(((Name) value)
                    .getFullyQualifiedName()));
            assign.setRightHandSide(ast.newName(tmpV.getName()
                    .getFullyQualifiedName()));
            ifStmt.setThenStatement(ast.newExpressionStatement(assign));

            Statement invocStmt = (Statement) ASTNodes.getParent(oldInvoc,
                    Statement.class);
            ASTNode parent = invocStmt.getParent();
            if (parent instanceof Block) {
                rewriter.replace(invocStmt, tmpVDecl, null);
                rewriter.getListRewrite(parent,
                        ((Block) parent).STATEMENTS_PROPERTY).insertAfter(
                        ifStmt, invocStmt, null);
            } else {
                Block block = ast.newBlock();
                block.statements().add(tmpVDecl);
                block.statements().add(ifStmt);
                rewriter.replace(invocStmt, block, null);
            }
        } else {
            rewriter.replace(oldInvoc, newInvoc, null);
        }
        return putVName;
    }

    private void refactoringForReplace(MethodInvocation oldInvoc) {
        PatternDetectionAction.isRewrite = true;
        AST ast = oldInvoc.getAST();
        MethodInvocation newInvoc = (MethodInvocation) ASTNode.copySubtree(ast,
                oldInvoc);
        newInvoc.setName(ast.newSimpleName("replace"));
        rewriter.replace(oldInvoc, newInvoc, null);
        changeMapType(ast);
    }

    private void refactoringForRemove(IfStatement ifStmt,
            MethodInvocation invocRemove, MethodInvocation invocGet) {
        if (invocGet == null)
            return;
        PatternDetectionAction.isRewrite = true;

        AST ast = ifStmt.getAST();
        String invockedName = invocGet.getName().getIdentifier();
        if (invockedName.equals("get")) {
            if (ASTNodes.isParent(invocGet, ifStmt)) {
                new RefactoringUtil(rewriter, info, fFieldBinding)
                        .refactoringIfStmt(ifStmt, invocRemove, invocGet, ast,
                                true);
            } else {
                Statement removeStmt = (Statement) ASTNodes.getParent(
                        invocRemove, Statement.class);
                if (removeStmt instanceof ExpressionStatement) {
                    Expression e = ((ExpressionStatement) removeStmt)
                            .getExpression();
                    InfixExpression infix = (InfixExpression) ifStmt
                            .getExpression();
                    if (e instanceof Assignment
                            && ((Assignment) e).getLeftHandSide().toString()
                                    .equals(infix.getLeftOperand().toString())) {
                        MethodInvocation newInvoc = (MethodInvocation) ASTNode
                                .copySubtree(ast, invocRemove);
                        ASTNode parent = ASTNodes.getParent(invocRemove,
                                Statement.class);
                        rewriter.replace(invocGet, newInvoc, null);
                        rewriter.remove(parent, null);
                    }
                }
            }
        } else if (invockedName.equals("containsKey")) {
            new RefactoringUtil(rewriter, info, fFieldBinding)
                    .refactoringIfStmt(ifStmt, invocRemove, invocGet, ast,
                            false);
        }
    }

    private void invokeRefactoring(int refactoringType, String className,
            IfStatement ifStmt, MethodInvocation invocation,
            MethodInvocation invocGet) {
        if (rewriter == null || refactoredLine.contains(className)) {
            return;
        }
        refactoredLine.add(className);

        switch (refactoringType) {
        case PUT_FIX:
            refactoringForPut(invocation);
            break;
        case REMOVE_FIX:
            refactoringForRemove(ifStmt, invocation, invocGet);
            break;
        case REPLACE_FIX:
            refactoringForReplace(invocation);
            break;
        default:
            break;
        }
    }
}