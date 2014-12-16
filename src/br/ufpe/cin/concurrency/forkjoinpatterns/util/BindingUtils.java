package br.ufpe.cin.concurrency.forkjoinpatterns.util;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ExpressionStatement;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.InfixExpression;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.NullLiteral;
import org.eclipse.jdt.core.dom.ParenthesizedExpression;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.SuperFieldAccess;
import org.eclipse.jdt.core.dom.SynchronizedStatement;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.internal.corext.dom.ASTNodes;
import org.eclipse.jdt.internal.corext.dom.Bindings;
import org.eclipse.jdt.internal.corext.dom.JdtASTMatcher;

import br.ufpe.cin.concurrency.forkjoinpatterns.detectors.CollectVariableInfo;

public class BindingUtils {
    public static Set<String> queueType = new HashSet<String>();
    public static Set<String> copyOnWrite = new HashSet<String>();
    public static Set<String> mapType = new HashSet<String>();
    public static Set<String> allType = new HashSet<String>();

    static {
        queueType.add("ArrayBlockingQueue");
        queueType.add("ConcurrentLinkedQueue");
        queueType.add("LinkedBlockingDeque");
        queueType.add("LinkedBlockingQueue");
        queueType.add("PriorityBlockingQueue");
        queueType.add("SynchronousQueue");
        queueType.add("BlockingDeque");
        queueType.add("BlockingQueue");

        copyOnWrite.add("CopyOnWriteArrayList");
        copyOnWrite.add("CopyOnWriteArraySet");

        mapType.add("ConcurrentHashMap");
        mapType.add("ConcurrentMap");
        
        allType.addAll(queueType);
        allType.addAll(copyOnWrite);
        allType.addAll(mapType);
    }

    public static boolean compareArguments(Expression e1, Expression e2) {
        boolean flag = false;
        flag = e1.subtreeMatch(new JdtASTMatcher(), e2);
        if (flag)
            return true;
        IBinding b1 = resolveBinding(e1);
        IBinding b2 = resolveBinding(e2);
        if (b1 != null && b2 != null && Bindings.equals(b1, b2))
            return true;
        return false;
    }

    public static boolean checkArgument(MethodInvocation methodInvoc,
            ASTNode hashMapKey) {
        boolean flag = false;
        ASTNode firstArgument = (ASTNode) methodInvoc.arguments().get(0);
        flag = firstArgument.subtreeMatch(new JdtASTMatcher(), hashMapKey);
        if (flag)
            return true;
        if (firstArgument instanceof SimpleName
                && hashMapKey instanceof SimpleName) {
            SimpleName arg1 = (SimpleName) firstArgument;
            SimpleName arg2 = (SimpleName) hashMapKey;
            IBinding b1 = arg1.resolveBinding();
            IBinding b2 = arg2.resolveBinding();
            if (b1 != null && b2 != null && Bindings.equals(b1, b2))
                return true;
        }
        return false;
    }

    public static IBinding resolveBinding(Expression expression) {
        if (expression instanceof SimpleName)
            return ((SimpleName) expression).resolveBinding();
        else if (expression instanceof QualifiedName)
            return ((QualifiedName) expression).resolveBinding();
        else if (expression instanceof FieldAccess)
            return ((FieldAccess) expression).getName().resolveBinding();
        else if (expression instanceof SuperFieldAccess)
            return ((SuperFieldAccess) expression).getName().resolveBinding();
        return null;
    }

    public static Assignment findAssignment(ASTNode stmt, IBinding binding,
            String name) {
        if (stmt == null)
            return null;
        ASTNode node = stmt.getParent();
        if (node instanceof MethodDeclaration) {
            return null;
        } else if (node instanceof Block) {
            Block block = (Block) node;
            List stmts = block.statements();
            for (Object o : stmts) {
                if (o == stmt)
                    break;
                if (o instanceof ExpressionStatement) {
                    Expression e = ((ExpressionStatement) o).getExpression();
                    if (e instanceof Assignment) {
                        Assignment assign = (Assignment) e;
                        Expression lhs = assign.getLeftHandSide();
                        if (lhs instanceof SimpleName
                                || lhs instanceof QualifiedName) {
                            IBinding ib = resolveBinding(lhs);
                            String vn = (lhs instanceof SimpleName) ? ((SimpleName) lhs)
                                    .getFullyQualifiedName()
                                    : ((QualifiedName) lhs)
                                            .getFullyQualifiedName();
                            if (binding != null && Bindings.equals(binding, ib)) {
                                return assign;
                            } else if (binding == null && name.equals(vn)) {
                                return assign;
                            }
                        }
                    }
                }
            }
            return findAssignment(block, binding, name);
        } else
            return findAssignment(node, binding, name);
    }

    public static IBinding findBindings(IBinding binding,
            CollectVariableInfo info) {
        if (binding != null) {
            for (IBinding ib : info.getVariableMap().keySet()) {
                if (ib != null && ib == binding) {
                    return ib;
                }
            }
            for (IBinding ib : info.getVariableMap().keySet()) {
                if (ib != null
                        && (Bindings.equals(ib, binding) || ib.toString()
                                .equals(binding.toString()))) {
                    return ib;
                }
            }
        }
        return null;
    }

    public static ASTNode getInitializer(ASTNode ifStatement, IBinding binding,
            String vName, CollectVariableInfo info) {
        Assignment assign = BindingUtils.findAssignment(ifStatement, binding,
                vName);
        ASTNode booleanVariableInitializer = null;
        if (assign != null) {
            booleanVariableInitializer = assign.getRightHandSide();
        } else {
            VariableDeclarationFragment declarationFragment = info
                    .getVariableMap().get(
                            BindingUtils.findBindings(binding, info));
            if (declarationFragment != null) {
                booleanVariableInitializer = declarationFragment
                        .getInitializer();
            }
        }
        return booleanVariableInitializer;
    }

    public static boolean checkMapType(String className,
            IBinding fFieldBinding, CollectVariableInfo info) {
        if (className.contains("<")) {
            className = className.substring(0, className.indexOf('<'));
        }
        if (mapType.contains(className)) {
            return true;
        } else {
            Expression e = info.getMapTypeVaraibleMap().get(
                    findBindings(fFieldBinding, info));
            for (String name : mapType)
                if (e != null && e.toString().contains(name)
                        && e.toString().contains("new"))
                    return true;
        }
        return false;
    }

    public static boolean checkQueueType(String className,
            IBinding fFieldBinding, CollectVariableInfo info) {
        if (className.contains("<")) {
            className = className.substring(0, className.indexOf('<'));
        }
        if (queueType.contains(className)) {
            return true;
        } else {
            Expression e = info.getQueueTypeVaraibleMap().get(
                    findBindings(fFieldBinding, info));
            for (String name : queueType)
                if (e != null && e.toString().contains(name)
                        && e.toString().contains("new"))
                    return true;
        }
        return false;
    }

    public static boolean checkCopyOnWriteType(String className,
            IBinding fFieldBinding, CollectVariableInfo info) {
        if (className.contains("<")) {
            className = className.substring(0, className.indexOf('<'));
        }
        if (copyOnWrite.contains(className)) {
            return true;
        } else {
            Expression e = info.getCopyOnWriteTypeVaraibleMap().get(
                    findBindings(fFieldBinding, info));
            for (String name : copyOnWrite)
                if (e != null && e.toString().contains(name)
                        && e.toString().contains("new"))
                    return true;
        }
        return false;
    }

    public static boolean considerBinding(IBinding binding,
            IBinding fFieldBinding) {
        if (!(binding instanceof IVariableBinding) || fFieldBinding == null)
            return false;
        boolean result = Bindings.equals(fFieldBinding, binding)
                || Bindings.equals(fFieldBinding,
                        ((IVariableBinding) binding).getVariableDeclaration());
        return result;
    }
    
    public static boolean hasSynchronized(ASTNode currNode) {
        ASTNode syn = ASTNodes.getParent(currNode, SynchronizedStatement.class);
        if (syn != null)
            return true;
        ASTNode node = ASTNodes.getParent(currNode, MethodDeclaration.class);
        if (node != null) {
            MethodDeclaration methodDecl = (MethodDeclaration) node;
            int modifier = methodDecl.getModifiers();
            if ((modifier & Modifier.SYNCHRONIZED) != 0)
                return true;
        }
        return false;
    }
}
