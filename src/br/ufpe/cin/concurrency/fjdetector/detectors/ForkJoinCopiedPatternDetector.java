package br.ufpe.cin.concurrency.fjdetector.detectors;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ExpressionStatement;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IfStatement;
import org.eclipse.jdt.core.dom.InfixExpression;
import org.eclipse.jdt.core.dom.InfixExpression.Operator;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.ReturnStatement;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.internal.corext.dom.Bindings;

import br.ufpe.cin.concurrency.fjdetector.Detector;
import br.ufpe.cin.concurrency.fjdetector.refactors.ForkJoinCopiedPatternRefactor;
import br.ufpe.cin.concurrency.fjdetector.util.BlackList;
import br.ufpe.cin.concurrency.fjdetector.util.Result;
import br.ufpe.cin.concurrency.fjdetector.util.Results;

public class ForkJoinCopiedPatternDetector extends ASTVisitor implements Detector{

	private List<ITypeBinding> datastructures = null;
	private Set<Result> results = new HashSet<Result>();
	private ForkJoinCopiedPatternRefactor refactor;
	private boolean isRefactoringAvailable = false;
	private ASTNode currentClass;
	
	public ForkJoinCopiedPatternDetector(ASTRewrite rewriter) {
		this.refactor = new ForkJoinCopiedPatternRefactor(rewriter);
	}

	@Override
	public boolean visit(TypeDeclaration clazz) {
		ITypeBinding superClass = clazz.resolveBinding().getSuperclass();
		if (superClass != null) {
			if (isForkJoinSubClass(superClass)) {
				this.datastructures = getDataStructureFields(clazz);
				this.currentClass = clazz;
				
				for(MethodDeclaration method: clazz.getMethods()) {
					if(isComputeMethod(method)) {
						analyzeComputeMethod(method);
					}
				}
			}
		}
		return true;
	}

	private boolean isComputeMethod(MethodDeclaration method) {
		return !method.isConstructor() && 
				method.parameters().size() == 0 && 
				method.getName().getIdentifier().equals("compute");
	}

	private boolean isForkJoinSubClass(ITypeBinding superClass) {
		return superClass.getName().equals("RecursiveAction") || superClass.getName().equals("RecursiveTask");
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
		List stms = method.getBody().statements();
		for (Object o: stms) {
			Statement s = (Statement) o;
			if (s instanceof IfStatement) {
                IfStatement ifstmt = (IfStatement) s;

                Expression ife = ifstmt.getExpression();
                analyzeSequentialCase(ife);
                
                Statement elsestmt = ifstmt.getElseStatement();
                analyzeParallelCase(elsestmt);
			}
		}
	}

	private void analyzeParallelCase(Statement elsestmt) {
		if (elsestmt instanceof IfStatement) {
        	IfStatement then = (IfStatement) elsestmt;
        	if (then.getThenStatement() instanceof Block) {
        		List statements = ((Block) then.getThenStatement()).statements();
				handleStatement(statements);
        	}
        	
        	System.out.println();
        }
	}

	private void analyzeSequentialCase(Expression ife) {
		if (ife instanceof InfixExpression) {
        	Expression leftOperand = ((InfixExpression) ife).getLeftOperand();
            Expression rightOperand = ((InfixExpression) ife).getRightOperand();
            Operator op = ((InfixExpression) ife).getOperator();
            
            boolean ds = isDataStructure(leftOperand);
            
//            System.out.println(leftOperand);
//            System.out.println(op);
//            System.out.println(rightOperand);
        }		
	}

	private void handleStatement(List stmts) {
		List<MethodInvocation> methodInvocs = new LinkedList<MethodInvocation>();
		
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
		}

		for (MethodInvocation method : methodInvocs) {
			if (BlackList.has(method)) {
				for (Object args : method.arguments()) {
					if (args instanceof SimpleName) {
						ITypeBinding currentField = ((SimpleName) args).resolveTypeBinding();

						if (currentField.isArray() || isList(currentField)) {
							for (ITypeBinding ds : datastructures) {
								if(Bindings.equalDeclarations(currentField, ds)) {
									System.out.println("DETECTED!!!");
									System.out.println(method);
									isRefactoringAvailable = true;
									Result metadata = Results.getMetadata(method);
									results.add(metadata);
									refactor.refactor(currentClass);
									return;
								}
							}
						}
					}
				}
			}
		}
	}        

	private boolean isDataStructure(Expression node) {
		ITypeBinding type = node.resolveTypeBinding();
		return type.isArray() || isList(type);
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

	@Override
	public Set<Result> getResults() {
        return results;
    }
	
	@Override
	public boolean isRefactoringAvailable() {
		return isRefactoringAvailable;
	}
}
