package pasito.staticSemantics;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;

import pasito.ast.PasitoVisitor;
import pasito.ast.Program;
import pasito.ast.declaration.ConstDecl;
import pasito.ast.declaration.Declaration;
import pasito.ast.declaration.TypeDecl;
import pasito.ast.declaration.VarDecl;
import pasito.ast.element.ExpressionElement;
import pasito.ast.element.KeyedElement;
import pasito.ast.element.LiteralElement;
import pasito.ast.expression.BinaryExpression;
import pasito.ast.expression.BinaryOperator;
import pasito.ast.expression.BooleanLiteral;
import pasito.ast.expression.CallExpression;
import pasito.ast.expression.CompositeLit;
import pasito.ast.expression.Expression;
import pasito.ast.expression.FloatLiteral;
import pasito.ast.expression.FullSliceExpression;
import pasito.ast.expression.FunctionLiteral;
import pasito.ast.expression.IdExpression;
import pasito.ast.expression.IndexExpression;
import pasito.ast.expression.IntLiteral;
import pasito.ast.expression.MethodExpression;
import pasito.ast.expression.SelectorExpression;
import pasito.ast.expression.SliceExpression;
import pasito.ast.expression.UnaryExpression;
import pasito.ast.expression.UnaryOperator;
import pasito.ast.methodSpecOrInterfaceName.InterfaceName;
import pasito.ast.methodSpecOrInterfaceName.MethodSpec;
import pasito.ast.signature.FormalParameter;
import pasito.ast.signature.Signature;
import pasito.ast.statement.Assignment;
import pasito.ast.statement.Block;
import pasito.ast.statement.DeclarationStm;
import pasito.ast.statement.EmptyStmt;
import pasito.ast.statement.ExpressionStmt;
import pasito.ast.statement.ForRange;
import pasito.ast.statement.ForStmt;
import pasito.ast.statement.IfElseStmt;
import pasito.ast.statement.IfStmt;
import pasito.ast.statement.ReturnStmt;
import pasito.ast.statement.ShortVarDecl;
import pasito.ast.statement.Statement;
import pasito.ast.topLevelDecl.Dec;
import pasito.ast.topLevelDecl.FunctionDecl;
import pasito.ast.topLevelDecl.MethodDecl;
import pasito.ast.topLevelDecl.TopLevelDecl;
import pasito.ast.type.ArrayType;
import pasito.ast.type.FieldDecl;
import pasito.ast.type.InterfaceType;
import pasito.ast.type.PointerType;
import pasito.ast.type.SliceType;
import pasito.ast.type.StructType;
import pasito.ast.type.TypeName;
import pasito.staticEnvironment.AlreadyBoundException;
import pasito.staticEnvironment.InvalidLevelException;
import pasito.staticEnvironment.SymbolTable;
import pasito.staticSemantics.binding.Binding;
import pasito.staticSemantics.binding.Const;
import pasito.staticSemantics.binding.Fun;
import pasito.staticSemantics.binding.Ty;
import pasito.staticSemantics.binding.Var;
import pasito.staticSemantics.type.ArrayTp;
import pasito.staticSemantics.type.Kind;
import pasito.staticSemantics.type.PointerTp;
import pasito.staticSemantics.type.Primitive;
import pasito.staticSemantics.type.Type;
import pasito.staticSemantics.type.Untyped;
import pasito.util.ErrorRegister;

public class Analyser implements PasitoVisitor {
	SymbolTable<Binding> env;
    public ErrorRegister erros = new ErrorRegister();

	public Analyser() {
		 env = new SymbolTable<>();
		 
			 try {
				env.put("int", new Ty(Primitive.INT32));
				env.put("float", new Ty(Primitive.FLOAT64));
				env.put("boolean", new Ty(Primitive.BOOLEAN));
				// .. Todos os primitivos
			} catch (AlreadyBoundException e) {
				e.printStackTrace();
			}
	}

	@Override
	public Object VisitProgram(Program program) {
		for (TopLevelDecl tpDec :  program.declarations )
			tpDec.accept(this);
		return null;
	}

	@Override
	public Object VisitDec(Dec dec) {
		dec.decl.accept(this);
		return null;
	}

	@Override
	public Object VisitFunctionDecl(FunctionDecl functionDecl) {
		try {
			Binding fun = (Binding) functionDecl.sig.accept(this);
			env.put(functionDecl.name, fun);
			env.beginScope();
			functionDecl.body.accept(this);
			env.endScope();
		} catch (AlreadyBoundException | InvalidLevelException e) {
			erros.report("Função \"" + functionDecl.name + "\"" + e.getMessage());
		}
		return null;
	}

	@Override
	public Object VisitMethodDecl(MethodDecl methodDecl) {
		methodDecl.sig.accept(this); // visita a assinatura
		methodDecl.receiver.accept(this); // visita o receiver
		methodDecl.body.accept(this); // visita o corpo 
		return null;
	}

	@Override
	public Object VisitSignature(Signature signature) { 
		List<Type> pin = new LinkedList<>();
		List<Type> pout = new LinkedList<>();
		Type variadic = null;
		
		if (signature.inPars != null) 
			for(FormalParameter fp : signature.inPars) {
				Type ty = (Type) fp.type.accept(this);
				pin.add(ty);
			}
		
		if (signature.outPars != null) 
		for(FormalParameter fp : signature.outPars) {
			Type ty = (Type) fp.type.accept(this);
			pout.add(ty);
		}
		
		if (signature.variadicPar != null) 
			variadic = (Type) signature.variadicPar.accept(this);
		
		Binding fun = new Fun(pout, pin, variadic);		
		return fun;
	}

	@Override
	public Object VisitConstDecl(ConstDecl constDecl) { 
		if (constDecl.exp == null) {
			erros.report("error: Declaração de Constante NULA");
			return null; 
		} 
		
		Object value =  constDecl.exp.accept(this);
		Type ty = (Type) constDecl.exp.accept(this);
		try {
			if (constDecl.type == null)
					env.put(constDecl.name, new Const(value, ty) );
			else {
			   Type declaredType = (Type) constDecl.type.accept(this);
			   if ( !ty.assignableTo(declaredType) )
				   erros.report("Não é possivel atribuir um " + ty + " para um tipo " + declaredType);
			   else
				   env.put(constDecl.name, new Const(value, declaredType) );
			}
		} catch (AlreadyBoundException e) {
			erros.report("\"" + constDecl.name + "\"" + e.getMessage());
		}	   
		return null;
	}

	@Override
	public Object VisitVarDecl(VarDecl varDecl) { 
		Iterator<String> idIt = varDecl.names.iterator();
		if (varDecl.exps != null) {
			Iterator<Expression> expIt = varDecl.exps.iterator();
			
			while (idIt.hasNext() && expIt.hasNext()){
				String id = idIt.next();
				try {
					Type ty = (Type) expIt.next().accept(this);

					if (varDecl.type != null) {
						Type declaredType = (Type) varDecl.type.accept(this);
						if (!ty.assignableTo(declaredType))
							env.put(id, new Var(ty));
					}
					else 
						env.put(id, new Var((Type) ty));	
				} catch (AlreadyBoundException e) {
					erros.report("Variável \"" + id + "\" já declarada no escopo atual");
				}		   
			}
		}
		
        return null;
	}

	@Override
	public Object VisitTypeDecl(TypeDecl typeDecl) {
		try {
		   Type declaredType = (Type) typeDecl.type.accept(this);
		   env.put(typeDecl.name, new Ty(declaredType));
		} catch (AlreadyBoundException e) {
			erros.report("Tipo \"" + typeDecl.name + "\"" + e.getMessage());
		}	   
		return null;
	}

	@Override
	public Object VisitFormalParameter(FormalParameter formalParameter) {
		try {
			if (formalParameter.type != null) {
				Type declaredType = (Type) formalParameter.type.accept(this);
				env.put(formalParameter.name, new Ty(declaredType));
			}
		} catch (AlreadyBoundException e) {
			erros.report("Parametro \"" + formalParameter.name + "\"" + e.getMessage());
		}	   
		return null;
	}

	@Override
	public Object VisitTypeName(TypeName typeName) {
		Binding b = env.get(typeName.name);
		if (b != null) {
			return ((Ty) b).type;
		}
		return null;
	}

	@Override
	public Object VisitArrayType(ArrayType arrayType) {
		Type tEle = (Type) arrayType.elemType.accept(this);
		Object value = arrayType.length.accept(this);
		int v;
		if(value instanceof IntLiteral) {			
			v = ((IntLiteral)value).value;			
			return new ArrayTp(v, tEle);
		} 
		else {
			erros.report("Tamanho do Array invalido!");
			return null;
		}
	}

	@Override
	public Object VisitBaseType(PointerType pointerType) {
		Type ty = (Type) pointerType.baseType.accept(this);		
		return new PointerTp(ty);
	}

	@Override
	public Object VisitStructType(StructType structType) {
		for (FieldDecl elem : structType.fieldDecls) {
			elem.accept(this);
		}
		return null;
	}

	@Override
	public Object VisitInterfaceType(InterfaceType interfaceType) {
		//Desconsiderado pelo professor para o projeto
		return null;
	}

	@Override
	public Object VisitSliceType(SliceType sliceType) {
		sliceType.elementType.accept(this);
		return null;
	}

	@Override
	public Object VisitFieldDecl(FieldDecl fieldDecl) {
		try {
			if (fieldDecl.type != null) {
				Type declaredType = (Type) fieldDecl.type.accept(this);
				env.put(fieldDecl.name, new Ty(declaredType));
			}  
		} catch (AlreadyBoundException e) {
			erros.report("\"" + fieldDecl.name + "\"" + e.getMessage());
		}	   
		return null;
	}

	@Override
	public Object VisitMethodSpec(MethodSpec methodSpec) {
		try {
			Binding fun = (Binding) methodSpec.sig.accept(this);
			env.put(methodSpec.name, fun);
		} catch (AlreadyBoundException e) {
			erros.report("Method \"" + methodSpec.name + "\"" + e.getMessage());
		}
		return null;
	}

	@Override
	public Object VisitInterfaceName(InterfaceName interfaceName) {
		return null;
	}

	@Override
	public Object VisitUnaryExpression(UnaryExpression unaryExpression) {
		Object value = unaryExpression.exp.accept(this);
		if (value != null)
			switch (unaryExpression.op) {
			case PLUS: 
				if (value instanceof Integer || value instanceof Float)
					return value;
				else {
					erros.report("Not numeric operand");
					return null;
				}
			case MINUS:
				if (value instanceof Integer)
					return - (Integer) value;
				else if (value instanceof Float)
					return - (Float) value;
				else {
					erros.report("Not numeric operand");
					return null;
				}
			case NOT:
				if (value instanceof Boolean)
					return ! ((Boolean) value).booleanValue();
				else {
					erros.report("Not boolean operand");
					return null;
				}
			case MULT: 
			default:
				erros.report("Cannot point to a constant value");
				return null;
			}
		else
			return null;
	}

	@Override
	public Object VisitBinaryExpression(BinaryExpression binaryExpression) {
		Type tipoRetorno = null, tEsq, tDir;
        Object esq = binaryExpression.leftExp.accept(this);
        Object dir = binaryExpression.rightExp.accept(this);
        
        tEsq = (esq instanceof Var | esq instanceof Const)
                ? ((Var) esq).type
                : (Type) esq;
        
        tDir = (dir instanceof Var | dir instanceof Const)
                ? ((Var) dir).type
                : (Type) dir;
                
        switch (binaryExpression.op.name()) {
	        case "AND":
			case "OR":
			case "PLUS":
			case "MINUS":
			case "MULT":
			case "DIV":
			case "LT":
			case "ASSIGN":
				if (tEsq != null && tDir != null) {
					if (tEsq.equivalent(tDir)) {
		                tipoRetorno = new Primitive(Kind.BOOLEAN);
		            }
		            else if (tEsq.equivalent(Primitive.FLOAT64) && tDir.equivalent(Primitive.INT32)) {
		            	tDir = Primitive.FLOAT64;
		                tipoRetorno = Primitive.BOOLEAN;
		            }
		            else if (tEsq.equivalent(Primitive.INT32) && tDir.equivalent(Primitive.FLOAT64)) {
		            	tEsq = Primitive.INT32;
		            	tipoRetorno = Primitive.BOOLEAN;
		            }
		            else {
		                erros.report("Impossível realizar a operação ["
		                        + binaryExpression.op.name() +  "] entre os tipos " + tEsq
		                        + " e " + tDir); 
		                // Retornando bool apenas para prosseguir a checagem sem erros
		                tipoRetorno = Primitive.BOOLEAN;
		            }
				} else
					erros.report("Atribuição Inválida!"); 
	                // Retornando bool apenas para prosseguir a checagem sem erros
	                tipoRetorno = Primitive.BOOLEAN;				
	            break;
			case "EQ":
				if (tEsq != null && tDir != null) {
					if (tEsq.equivalent(tDir)) {
		                tipoRetorno = new Primitive(Kind.BOOLEAN);
		            } else {
		            	erros.report("Impossível realizar a operação ["
		                        + binaryExpression.op.name() +  "] entre os tipos " + tEsq
		                        + " e " + tDir); 
		            }
				} else
					erros.report("Atribuição Inválida!"); 
	                // Retornando bool apenas para prosseguir a checagem sem erros
	                tipoRetorno = Primitive.BOOLEAN;	
				break;
			default:
				return null;
		}
        return tipoRetorno;
	}

	@Override
	public Object VisitIntLiteral(IntLiteral intLiteral) {
		return Primitive.INT32;
	}

	@Override
	public Object VisitFloatLiteral(FloatLiteral floatLiteral) {
		return Primitive.FLOAT64;
	}

	@Override
	public Object VisitFunctionLiteral(FunctionLiteral functionLiteral) {
		try {
			functionLiteral.sig.accept(this);
			env.beginScope();
			functionLiteral.body.accept(this);
			env.endScope();
		} catch (InvalidLevelException e) {
			erros.report(e.getMessage());
		}
		return null;
	}

	@Override
	public Object VisitCompositLit(CompositeLit compositeLit) {
		if (compositeLit.type != null)
			compositeLit.type.accept(this);
  
		for (KeyedElement iterable_element : compositeLit.elems) {
			iterable_element.accept(this);
		}
		return null;
	}

	@Override
	public Object VisitIdExpression(IdExpression idExpression) {
		return new Var(Primitive.INT32);
	}

	@Override
	public Object VisitMethodExpression(MethodExpression methodExpression) {
		try {
			if (methodExpression.type != null) {
				Type declaredType = (Type) methodExpression.type.accept(this);
				env.put(methodExpression.name, new Ty(declaredType));
			}  
		} catch (AlreadyBoundException e) {
			erros.report("\"" + methodExpression.name + "\"" + e.getMessage());
		}	   
		return null;
	}

	@Override
	public Object VisitSelectorExpression(SelectorExpression selectorExpression) {
		try {
			if (selectorExpression.exp != null) {
				Type declaredType = (Type) selectorExpression.exp.accept(this);
				env.put(selectorExpression.name, new Ty(declaredType));
			}  
		} catch (AlreadyBoundException e) {
			erros.report("\"" + selectorExpression.name + "\"" + e.getMessage());
		}	   
		return null;
	}

	@Override
	public Object VisitIndexExpression(IndexExpression indexExpression) {
		if (indexExpression.exp != null) 
			indexExpression.exp.accept(this);
		if (indexExpression.indexExp != null) 
			indexExpression.indexExp.accept(this);
		return null;
	}

	@Override
	public Object VisitSliceExpression(SliceExpression sliceExpression) {
		if (sliceExpression.exp != null) 
			sliceExpression.exp.accept(this);
		if (sliceExpression.high != null) 
			sliceExpression.high.accept(this);
		if (sliceExpression.low != null) 
			sliceExpression.low.accept(this);
		return null;
	}

	@Override
	public Object VisitFullSliceExpression(FullSliceExpression fullSliceExpression) {
		if (fullSliceExpression.exp != null) 
			fullSliceExpression.exp.accept(this);
		if (fullSliceExpression.high != null) 
			fullSliceExpression.high.accept(this);
		if (fullSliceExpression.low != null) 
			fullSliceExpression.low.accept(this);
		if (fullSliceExpression.max != null) 
			fullSliceExpression.max.accept(this);
		return null;
	}

	@Override
	public Object VisitCallExpression(CallExpression callExpression) {
		try {
			Binding b = (Binding) env.get(((IdExpression) callExpression.exp).name);
			if (b != null) {
				for (Expression element : callExpression.args) {
					element.accept(this);
				}
				
				if (callExpression.variadicArg != null) {
					callExpression.variadicArg.accept(this);
				}
			}
		} catch (Exception e) {
			erros.report("Não foi possível fazer a chamada.");
		}
		
		return null;
	}

	@Override
	public Object VisitKeyedElement(KeyedElement keyedElement) {
		keyedElement.exp.accept(this);
		keyedElement.elem.accept(this);
		return null;
	}

	@Override
	public Object VisitExpressionElement(ExpressionElement expressionElement) {
		expressionElement.exp.accept(this);
		return null;
	}

	@Override
	public Object VisitLiteralElement(LiteralElement literalElement) {
		for (KeyedElement elem : literalElement.keyedElems) {
			elem.accept(this);
		}
		return null;
	}

	@Override
	public Object VisitDeclarationStm(DeclarationStm declarationStm) {
		declarationStm.decl.accept(this);
		return null;
	}

	@Override
	public Object VisitEmptyStmt(EmptyStmt emptyStmt) {
		return null;
	}

	@Override
	public Object VisitReturnStmt(ReturnStmt returnStmt) {
		if (returnStmt.exps != null) 
			for (Expression e: returnStmt.exps) {
				e.accept(this);
			}
		return null;
	}

	@Override
	public Object VisitExpressionStmt(ExpressionStmt expressionStmt) {
		expressionStmt.exp.accept(this);
		return null;
	}

	@Override
	public Object VisitAssignment(Assignment assignment) {
		List<Expression> leftExps = assignment.leftExps;
		List<Expression> rightExps = assignment.rightExps;
		if(leftExps.size() != rightExps.size()) {
			erros.report("Declaração de atribuição inválida. É necessário que os termos a esquerda e direita sejam do mesmo tamanho.");
		}else {
			ListIterator<Expression> lIt = leftExps.listIterator();
			for(Expression rexp : rightExps) {
				
				Binding b = null;
				Expression lexp = lIt.next();
				if (lexp instanceof IdExpression) {
					b = (Binding) env.get(((IdExpression) lexp).name);
				}
				
				if (lexp instanceof IndexExpression) {
					IndexExpression index = ((IndexExpression) lexp);
					b = (Binding) env.get(((IdExpression) index.exp).name);
				}
				
				if (b == null) {
					erros.report("Não é possivel fazer esta atribuição pois não foi possivel encontrar o elemento no escopo atual");
				}
				
				Type rTy = null;
				if (rexp.accept(this) instanceof Var)
					rTy = ((Var) rexp.accept(this)).type;
				else
					rTy = (Type) rexp.accept(this);
				
				if (b instanceof Var) {
					if (((Var) b).type != null) {
						if(!rTy.assignableTo(((Var) b).type)) // testa se a atribuição é equivalentes
							erros.report("Não é possivel fazer esta atribuição!");
					}
					
				}				
			}
		}
		return null;
	}

	@Override
	public Object VisitShortVarDecl(ShortVarDecl shortVarDecl) {
		Iterator<String> idIt = shortVarDecl.names.iterator();
		Iterator<Expression> expIt = shortVarDecl.exps.iterator();
		
		while (idIt.hasNext() && expIt.hasNext()){
			String id = idIt.next();
			try {
				Object v = expIt.next().accept(this);
				if (v instanceof Var) 				
					env.put(id, (Var) v);
				else
					env.put(id, new Var((Type) v));
			} catch (AlreadyBoundException e) {
				 erros.report("A variável " + id + " já foi declarada neste escopo");
			}
		}

        return null;
	}

	@Override
	public Object VisitBlock(Block block) {
		for (Statement stm : block.stmts) {
			stm.accept(this);
		}
		return null;
	}

	@Override
	public Object VisitIfStmt(IfStmt ifStmt) {
		try {
			Type t = (Type) ifStmt.exp.accept(this);
	        if (! t.equals(Kind.BOOLEAN)) {
	            erros.report("Esperada uma expressão do tipo boolean, recebido argumento tipo" + t + " inválido.");
	        }

	        env.beginScope();
	        if (ifStmt.initStmt != null)
	        	ifStmt.initStmt.accept(this);
	        ifStmt.block.accept(this);
			env.endScope();
		} catch (InvalidLevelException e) {
			erros.report(e.getMessage());
		}
        return null;
	}

	@Override
	public Object VisitIfElseStmt(IfElseStmt ifElseStmt) {
		try {
			Type t = (Type) ifElseStmt.exp.accept(this);
			if (t != null)
		        if (!t.equivalent(Primitive.BOOLEAN)) {
		            erros.report("Esperada uma expressão do tipo boolean, recebido argumento tipo" + t + " inválido.");
		        }
	        
	        env.beginScope();
	        if (ifElseStmt.initStmt != null) {
	            ifElseStmt.initStmt.accept(this);
			}
	        
	        ifElseStmt.leftBlock.accept(this);
			env.endScope();
	
			env.beginScope();
	        ifElseStmt.rightBlock.accept(this);
	        env.endScope();
		} catch (InvalidLevelException e) {
			erros.report(e.getMessage());
		}
		
        return null;
	}

	@Override
	public Object VisitForStmt(ForStmt forStmt) {
		try {
			if (forStmt.initStmt != null) {
				forStmt.initStmt.accept(this);
			
				if (forStmt.exp != null) 
					forStmt.exp.accept(this);
				
				if (forStmt.postStmt != null)
					forStmt.postStmt.accept(this);
			} else {
				if (forStmt.exp != null) 
					forStmt.exp.accept(this);
			}
	
			env.beginScope();
			forStmt.body.accept(this);
			env.endScope();
		} catch (InvalidLevelException e) {
			erros.report(e.getMessage());
		}
		return null;
	}

	@Override
	public Object VisitForRange(ForRange forRange) {
		try {
			if (forRange.exp != null) {
				for (Expression e : forRange.exp) {
					e.accept(this);
				}
			}
			
			forRange.rangExp.accept(this);
			
			env.beginScope();
			forRange.body.accept(this);		
			env.endScope();
		} catch (InvalidLevelException e) {
			erros.report(e.getMessage());
		}
		return null;	
	}

	@Override
	public Object VisitBinaryOperator(BinaryOperator binaryOperator) {
		return null;
	}

	@Override
	public Object VisitUnaryOperator(UnaryOperator unaryOperator) {
		return null;
	}

	@Override
	public Object VisitBoolLiteral(BooleanLiteral boolLiteral) {
		return Primitive.BOOLEAN;
	}

	@Override
	public Object IncrStmt(pasito.ast.statement.IncrStmt incrStmt) {
		incrStmt.exp.accept(this);
		return null;
	}
}
