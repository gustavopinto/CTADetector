package concurrentpatterns.detection;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.ITypeBinding;
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
				List<ITypeBinding> datastructures = getDataStructureFields(node);
				
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
			System.out.print(s);
		}
				
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

	private List<ITypeBinding> getDataStructureFields(TypeDeclaration node) {
		List<ITypeBinding> datastructures = new ArrayList<>();
		for (FieldDeclaration field: node.getFields()) {
			ITypeBinding currentField = field.getType().resolveBinding();
			
			if(currentField.isArray() || isList(currentField)) {
				datastructures.add(currentField);
			}
		}
		return datastructures;
	}

	private boolean isList(ITypeBinding currentField) {
		return currentField.getQualifiedName().contains("java.util.List");
	}
}