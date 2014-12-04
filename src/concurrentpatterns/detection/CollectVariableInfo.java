package concurrentpatterns.detection;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;

import concurrencypatterns.util.BindingUtils;

public class CollectVariableInfo extends ASTVisitor {
    private Map<IBinding, VariableDeclarationFragment> variableMap = new HashMap<IBinding, VariableDeclarationFragment>();
    private Set<IBinding> fieldSet = new HashSet<IBinding>();
    private Map<IBinding, Expression> allMapVariables = new HashMap<IBinding, Expression>();
    private Map<IBinding, Expression> allQueueVariables = new HashMap<IBinding, Expression>();
    private Map<IBinding, Expression> allCopyOnWriteVariables = new HashMap<IBinding, Expression>();
    
    public Map<String, ASTNode> mapVariablesForRewrite = new HashMap<String, ASTNode>();
    
    @Override
    public boolean visit(VariableDeclarationStatement vDeclStmt) {
        List frags = vDeclStmt.fragments();
        for (Object frag : frags) {
            VariableDeclarationFragment vDeclFrag = (VariableDeclarationFragment) frag;
            IBinding binding = vDeclFrag.getName().resolveBinding();
            variableMap.put(binding, vDeclFrag);
            addMapQueueVariables(vDeclFrag, binding, vDeclStmt);
        }
        return true;
    }
    
    @Override
    public boolean visit(FieldDeclaration fDeclStmt) {
        List frags = fDeclStmt.fragments();
        for (Object frag : frags) {
            VariableDeclarationFragment vDeclFrag = (VariableDeclarationFragment) frag;
            IBinding binding = vDeclFrag.getName().resolveBinding();
            variableMap.put(binding, vDeclFrag);
            fieldSet.add(binding);
            addMapQueueVariables(vDeclFrag, binding, fDeclStmt);
        }
        return true;
    }

    private void addMapQueueVariables(VariableDeclarationFragment vDeclFrag,
            IBinding binding, ASTNode declStmt) {
        if (binding instanceof IVariableBinding) {
            String type = getClassName(((IVariableBinding) binding).getType()
                    .getName());
            String name = binding.getName();
            if (type.equals("ConcurrentHashMap")
                    || type.equals("ConcurrentMap") || type.equals("Map")) {
                if (allMapVariables.get(binding) == null)
                    allMapVariables.put(binding, vDeclFrag.getInitializer());
                mapVariablesForRewrite.put(name, declStmt);
            } else if (BindingUtils.queueType.contains(type)
                    || type.equals("AbstractQueue") || type.equals("Queue")) {
                if (allQueueVariables.get(binding) == null)
                    allQueueVariables.put(binding, vDeclFrag.getInitializer());
            } else if (BindingUtils.copyOnWrite.contains(type)
                    || type.equals("List") || type.equals("AbstractSet")
                    || type.equals("AbstractCollection") || type.equals("Set")) {
                if (allCopyOnWriteVariables.get(binding) == null)
                    allCopyOnWriteVariables.put(binding,
                            vDeclFrag.getInitializer());
            }
        }
    }

    @Override
    public boolean visit(Assignment assignStmt) {
        IBinding binding = BindingUtils.resolveBinding(assignStmt
                .getLeftHandSide());
        Expression e = allMapVariables.get(binding);
        if (allMapVariables.containsKey(binding) && e == null) {
            allMapVariables.put(binding, assignStmt.getRightHandSide());
        } else if (binding instanceof IVariableBinding && ((IVariableBinding) binding)
                .getType() != null) {
            String className = getClassName(((IVariableBinding) binding)
                    .getType().getName());
            if (className.equals("ConcurrentHashMap")
                    || className.equals("ConcurrentMap")
                    || className.equals("Map")) {
                Expression rhs = assignStmt.getRightHandSide();
                if (rhs.toString().contains("Concurrent"))
                    allMapVariables.put(binding, rhs);
            } else if (BindingUtils.queueType.contains(className)
                    || className.equals("AbstractQueue")
                    || className.equals("Queue")) {
                Expression rhs = assignStmt.getRightHandSide();
                for(String queue : BindingUtils.queueType)
                    if(rhs.toString().contains(queue))
                        allQueueVariables.put(binding, rhs);
            } else if (BindingUtils.copyOnWrite.contains(className)
                    || className.equals("List")
                    || className.equals("AbstractSet")
                    || className.equals("AbstractCollection")
                    || className.equals("Set")) {
                Expression rhs = assignStmt.getRightHandSide();
                for(String copyOnWrite : BindingUtils.copyOnWrite)
                    if(rhs.toString().contains(copyOnWrite))
                        allCopyOnWriteVariables.put(binding, rhs);
            }
        }
        return true;
    }

    private String getClassName(String fullName) {
        if (fullName.contains("<")) {
            fullName = fullName.substring(0, fullName.indexOf('<'));
        }
        return fullName;
    }

    public Map<IBinding, VariableDeclarationFragment> getVariableMap() {
        return variableMap;
    }

    public Map<IBinding, Expression> getMapTypeVaraibleMap() {
        return allMapVariables;
    }

    public Map<IBinding, Expression> getQueueTypeVaraibleMap() {
        return allQueueVariables;
    }

    public Map<IBinding, Expression> getCopyOnWriteTypeVaraibleMap() {
        return allCopyOnWriteVariables;
    }
    
    public Set<IBinding> getFieldsSet() {
        return fieldSet;
    }
}
