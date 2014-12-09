package concurrentpatterns.detection;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IfStatement;
import org.eclipse.jdt.core.dom.InfixExpression;
import org.eclipse.jdt.core.dom.InfixExpression.Operator;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclaration;

public class ForkJoinCopiedPattern extends ASTVisitor {

	@Override
	public boolean visit(TypeDeclaration node) {
		ITypeBinding superClass = node.resolveBinding().getSuperclass();
		if (superClass != null) {
			if (superClass.getName().equals("RecursiveAction") || superClass.getName().equals("RecursiveTask")) {
				List<FieldDeclaration> datastructures = getDataStructureFields(node);
				
				for(MethodDeclaration method: node.getMethods()) {
					if(method.isConstructor()) {
						analyzeConstructor(method);
					}
					
					if(!method.isConstructor() && 
							method.parameters().size() == 0 && 
							method.getName().getIdentifier().equals("compute")) {
						analyzeComputeMethod(method);
					}
				}
			}
		}
		return true;
	}

	/**
	 * Check pre-conditions:
	 *  - the data structure should be copied
	 *  - the resulting variables should be passed to a new instance of the Task class
	 *  - the computation should be divided in two parts: 
	 *  	- (1) verify if the computation can be solved sequentially
	 *  	- (2) recursively create smaller parts of the computation
	 *  
	 * @param MethodDeclaration method
	 */
	private void analyzeComputeMethod(MethodDeclaration method) {
		System.out.println(method.getName());
		List stms = method.getBody().statements();
		for (Object o: stms) {
			Statement s = (Statement) o;
			if (s instanceof IfStatement) {
                IfStatement ifstmt = (IfStatement) s;
                Expression ife = ifstmt.getExpression();
                if (ife instanceof InfixExpression) {
                	Expression leftOperand = ((InfixExpression) ife).getLeftOperand();
                    Expression rightOperand = ((InfixExpression) ife).getRightOperand();
                    Operator op = ((InfixExpression) ife).getOperator();
                    
                    boolean ds = isDataStructure(leftOperand);
                    
//                    System.out.println(leftOperand);
//                    System.out.println(op);
//                    System.out.println(rightOperand);
                }
                
                Statement elsestmt = ifstmt.getElseStatement();
                
                if (elsestmt instanceof IfStatement) {
                	IfStatement then = (IfStatement) elsestmt;
                	if (then.getThenStatement() instanceof Block) {
                		List statements = ((Block) then.getThenStatement()).statements();
                		for (Object object : statements) {
							System.out.println(object);
						}
                	}
                	
                	System.out.println();
                }
                
//                if (elsestmt instanceof Block) {
//                	List statements = ((Block) elsestmt).statements();
//                	for (Object object : statements) {
//					}
//                }
//                System.out.println(elsestmt);
			}
		}
	}

	private boolean isDataStructure(Expression node) {
		ITypeBinding type = node.resolveTypeBinding();
		return type.isArray() || isList(type);
	}

	/** 
	 * Verifies if constructor initializes the data structure var
	 *  
	 * @param method
	 */
	private void analyzeConstructor(MethodDeclaration method) {
		for(Object param: method.parameters()) {
			VariableDeclaration var = (VariableDeclaration) param;
			ITypeBinding type = var.resolveBinding().getType();
//            System.out.println(var + " -> " + type);
		}		
	}

	private List<FieldDeclaration> getDataStructureFields(TypeDeclaration node) {
		List<FieldDeclaration> datastructures = new ArrayList<>();
		for (FieldDeclaration field: node.getFields()) {
			ITypeBinding currentField = field.getType().resolveBinding();
			
			if(currentField.isArray() || isList(currentField)) {
				datastructures.add(field);
			}
		}
		return datastructures;
	}

	private boolean isList(ITypeBinding currentField) {
		return currentField.getQualifiedName().contains("java.util.List");
	}
}