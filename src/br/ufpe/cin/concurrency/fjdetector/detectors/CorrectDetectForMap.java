package br.ufpe.cin.concurrency.fjdetector.detectors;

import java.util.HashSet;
import java.util.Set;

import org.eclipse.core.resources.IFile;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.CastExpression;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ExpressionStatement;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.IfStatement;
import org.eclipse.jdt.core.dom.InfixExpression;
import org.eclipse.jdt.core.dom.InfixExpression.Operator;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.NullLiteral;
import org.eclipse.jdt.core.dom.ParenthesizedExpression;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.internal.corext.dom.ASTNodes;
import org.eclipse.ui.console.MessageConsoleStream;

import br.ufpe.cin.concurrency.fjdetector.actions.CorrectDetectionAction;
import br.ufpe.cin.concurrency.fjdetector.actions.PatternDetectionAction;
import br.ufpe.cin.concurrency.fjdetector.util.BindingUtils;
import br.ufpe.cin.concurrency.fjdetector.util.Result;
import br.ufpe.cin.concurrency.fjdetector.util.Results;

public class CorrectDetectForMap extends ASTVisitor {
    private CollectVariableInfo info;
    private Set<Result> result = new HashSet<Result>();
    private Set<String> incorrectSet;

    public CorrectDetectForMap(CollectVariableInfo i, Set<String> iSet) {
        info = i;
        incorrectSet = iSet;
    }

    @Override
    public boolean visit(MethodInvocation invoc) {
        Expression e = invoc.getExpression();
        Object[] className = getLocation(invoc);
        String location = (String) className[0] + className[1];
        if (checkMapType(e)) {
            String name = invoc.getName().getIdentifier();
            if (name.equals("putIfAbsent")) {
                if (!incorrectSet.contains(location))
                    result.add(new Result("putIfAbsent",
                            (String) className[0], (String) className[1],
                            (IFile) className[2], false));
            } else if (name.equals("replace")) {
                if (invoc.arguments().size() == 2) {
                    ASTNode ancestor = ASTNodes.getParent(invoc,
                            IfStatement.class);
                    if (ancestor != null)
                        result.add(new Result("replace",
                                (String) className[0], (String) className[1],
                                (IFile) className[2], false));
                } else if (invoc.arguments().size() == 3)
                    result.add(new Result("conditional replace",
                            (String) className[0], (String) className[1],
                            (IFile) className[2], false));
            } else if (name.equals("remove")) {
                if (incorrectSet.contains(location)) {
                    if (invoc.arguments().size() == 1) {
                        result.add(new Result("remove",
                                (String) className[0], (String) className[1],
                                (IFile) className[2], false));
                    }
                    if (invoc.arguments().size() == 2)
                        result.add(new Result("conditional remove",
                                (String) className[0], (String) className[1],
                                (IFile) className[2], false));
                }
            }
        } else if (checkListType(e)) {
            String name = invoc.getName().getIdentifier();
            if (name.equals("addIfAbsent")) {
                result.add(new Result("addIfAbsent",
                        (String) className[0], (String) className[1],
                        (IFile) className[2], false));
            }
        }
        return true;
    }

    private boolean checkEquals(ASTNode node) {
        ASTNode parent = node.getParent();
        while (parent != null) {
            if (parent instanceof IfStatement) {
                String expression = ((IfStatement) parent).getExpression()
                        .toString();
                if (expression.contains("equals"))
                    return true;
            }
            parent = parent.getParent();
        }
        return false;
    }

    @Override
    public boolean visit(IfStatement ifStatement) {
        Expression ifExpression = ifStatement.getExpression();

        while (ifExpression instanceof ParenthesizedExpression) {
            ifExpression = ((ParenthesizedExpression) ifExpression)
                    .getExpression();
        }

        if (ifExpression instanceof InfixExpression) {
            Set<Expression> set = seperateInfixExpression((InfixExpression) ifExpression);
            for (Expression e : set) {
                if (e instanceof InfixExpression)
                    handleOperatorConditional(ifStatement, e);
            }
        }

        return true;
    }

    private void handleOperatorConditional(IfStatement ifStatement,
            Expression ifExpression) {
        Operator operator = ((InfixExpression) ifExpression).getOperator();
        InfixExpression testExpression = (InfixExpression) ifExpression;
        Expression leftOperand = testExpression.getLeftOperand();
        Expression rightOperand = testExpression.getRightOperand();

        if (rightOperand instanceof NullLiteral
                && operator.equals(InfixExpression.Operator.NOT_EQUALS)) {
            while (leftOperand instanceof ParenthesizedExpression) {
                leftOperand = ((ParenthesizedExpression) leftOperand)
                        .getExpression();
            }
            if (leftOperand instanceof SimpleName
                    || leftOperand instanceof QualifiedName) {
                String name = (leftOperand instanceof SimpleName) ? ((SimpleName) leftOperand)
                        .getFullyQualifiedName()
                        : ((QualifiedName) leftOperand).getFullyQualifiedName();
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
            } else if (leftOperand instanceof Assignment) {
                Assignment assign = (Assignment) leftOperand;
                Expression e = assign.getRightHandSide();
                if (e instanceof MethodInvocation)
                    handleNullConditional(ifStatement, operator,
                            (MethodInvocation) e);
            }
        }
    }

    private void handleNullConditional(IfStatement ifStatement,
            Operator operator, MethodInvocation nullInitializerMethod) {
        IBinding fFieldBinding = BindingUtils
                .resolveBinding(nullInitializerMethod.getExpression());
        if (fFieldBinding instanceof IVariableBinding
                && ((IVariableBinding) fFieldBinding).getType() != null) {
            String className = ((IVariableBinding) fFieldBinding).getType()
                    .getName();
            if (BindingUtils.checkMapType(className, fFieldBinding, info)) {
                if (nullInitializerMethod.getName().getIdentifier()
                        .equals("get")) {
                    Object[] location = getLocation(nullInitializerMethod);
                    result.add(new Result("Get", (String) location[0],
                            (String) location[1], (IFile) location[2], false));
                }
            }
        }
    }

    private Object[] getLocation(MethodInvocation invoc) {
        CompilationUnit unit = (CompilationUnit) invoc.getRoot();
        return Results.getClassNameAndLine(unit, invoc);
    }

    private boolean checkMapType(Expression e) {
        IBinding vBinding = BindingUtils.resolveBinding(e);
        if (vBinding instanceof IVariableBinding
                && ((IVariableBinding) vBinding).getType() != null) {
            String className = ((IVariableBinding) vBinding).getType()
                    .getName();
            if (BindingUtils.checkMapType(className, vBinding, info)) {
                return true;
            }
        }
        return false;
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
            if (e instanceof NullLiteral)
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
            if (e instanceof NullLiteral)
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

    private boolean checkListType(Expression e) {
        IBinding vBinding = BindingUtils.resolveBinding(e);
        if (vBinding instanceof IVariableBinding
                && ((IVariableBinding) vBinding).getType() != null) {
            String className = ((IVariableBinding) vBinding).getType()
                    .getName();
            if (BindingUtils.checkCopyOnWriteType(className, vBinding, info)) {
                return true;
            }
        }
        return false;
    }

    public Set<Result> getResults() {
        return result;
    }
}
