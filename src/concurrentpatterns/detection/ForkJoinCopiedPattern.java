package concurrentpatterns.detection;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.internal.core.SourceMethod;

public class ForkJoinCopiedPattern extends ASTVisitor {

	@Override
	public boolean visit(TypeDeclaration node) {
		ITypeBinding superClass = node.resolveBinding().getSuperclass();
		if (superClass != null) {
			if (superClass.getName().equals("RecursiveAction") || superClass.getName().equals("RecursiveTask")) {
				List<ITypeBinding> datastructures = getDataStructureFields(node);
				
				for(MethodDeclaration method: node.getMethods()) {
					if(!method.isConstructor() && method.parameters().size() == 0 && method.getName().equals("compute")) {
						System.out.println(method.getBody());
						
//						method.accept(new ASTVisitor() {
//							@Override
//							public boolean visit(MethodDeclaration node) {
//								// TODO Auto-generated method stub
//								return super.visit(node);
//							}
//						});
					}
				}
			}
		}
		return true;
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