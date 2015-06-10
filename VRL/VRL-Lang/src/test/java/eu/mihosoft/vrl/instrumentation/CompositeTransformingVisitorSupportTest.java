package eu.mihosoft.vrl.instrumentation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.io.Reader;

import org.codehaus.groovy.ast.ASTNode;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.MethodNode;
import org.codehaus.groovy.ast.ModuleNode;
import org.codehaus.groovy.control.CompilationUnit;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.control.Phases;
import org.codehaus.groovy.control.SourceUnit;
import org.codehaus.groovy.control.customizers.ASTTransformationCustomizer;
import org.codehaus.groovy.transform.ASTTransformation;
import org.codehaus.groovy.transform.GroovyASTTransformation;
import org.junit.Test;

import eu.mihosoft.vrl.instrumentation.CompositeTransformingVisitorSupport.Root;
import eu.mihosoft.vrl.instrumentation.composites.BinaryExpressionPart;
import eu.mihosoft.vrl.instrumentation.composites.BreakPart;
import eu.mihosoft.vrl.instrumentation.composites.ClassNodePart;
import eu.mihosoft.vrl.instrumentation.composites.ContinuePart;
import eu.mihosoft.vrl.instrumentation.composites.DeclarationExpressionPart;
import eu.mihosoft.vrl.instrumentation.composites.FieldPart;
import eu.mihosoft.vrl.instrumentation.composites.ForLoopPart;
import eu.mihosoft.vrl.instrumentation.composites.IfStatementPart;
import eu.mihosoft.vrl.instrumentation.composites.MethodNodePart;
import eu.mihosoft.vrl.instrumentation.composites.ModuleNodePart;
import eu.mihosoft.vrl.instrumentation.composites.PostFixExpressionPart;
import eu.mihosoft.vrl.instrumentation.composites.ReturnStatementPart;
import eu.mihosoft.vrl.instrumentation.composites.WhileLoopPart;
import eu.mihosoft.vrl.lang.model.ClassDeclaration;
import eu.mihosoft.vrl.lang.model.CodeLineColumnMapper;
import eu.mihosoft.vrl.lang.model.CompilationUnitDeclaration;
import eu.mihosoft.vrl.lang.model.IdRequest;
import eu.mihosoft.vrl.lang.model.MethodDeclaration;
import eu.mihosoft.vrl.lang.model.Type;
import eu.mihosoft.vrl.lang.model.VisualCodeBuilder;
import eu.mihosoft.vrl.lang.model.VisualCodeBuilder_Impl;
import eu.mihosoft.vrl.workflow.FlowFactory;
import eu.mihosoft.vrl.workflow.IdGenerator;
import groovy.lang.GroovyShell;
import groovy.lang.Script;

public class CompositeTransformingVisitorSupportTest {

	static class ModuleTransformer implements
			TransformPart<ModuleNode, CompilationUnitDeclaration, Root> {
		VisualCodeBuilder builder;

		public ModuleTransformer(VisualCodeBuilder builder) {
			this.builder = builder;
		}

		@Override
		public CompilationUnitDeclaration transform(ModuleNode obj,
				Root parent, TransformContext context) {
			String packageName = "";
			if (obj.getPackageName() != null) {
				packageName = obj.getPackageName();
			}
			CompilationUnitDeclaration decl = builder.declareCompilationUnit(
					obj.getMainClassName() + ".groovy", packageName);
			parent.setRootObject(decl);
			return decl;
		}

		@Override
		public Class<ModuleNode> getAcceptedType() {
			return ModuleNode.class;
		}

		@Override
		public Class<Root> getParentType() {
			return Root.class;
		}

		@Override
		public void postTransform(CompilationUnitDeclaration obj,
				ModuleNode in, Root parent, TransformContext context) {

		}

	}

	static class ClassTransformer
			implements
			TransformPart<ClassNode, ClassDeclaration, CompilationUnitDeclaration> {
		VisualCodeBuilder builder;
		CompilationUnitDeclaration rootScope;

		public ClassTransformer(VisualCodeBuilder builder) {
			this.builder = builder;
			this.rootScope = builder.declareCompilationUnit(
					"test" + this.hashCode(), this.getClass().getPackage()
							.getName());
		}

		@Override
		public ClassDeclaration transform(ClassNode obj,
				CompilationUnitDeclaration parent, TransformContext context) {
			ClassDeclaration cls = builder.declareClass(parent, new Type("Test"
					+ obj.getName(), false));
			return cls;
		}

		@Override
		public Class<ClassNode> getAcceptedType() {
			return ClassNode.class;
		}

		@Override
		public Class<CompilationUnitDeclaration> getParentType() {
			return CompilationUnitDeclaration.class;
		}

		@Override
		public void postTransform(ClassDeclaration obj, ClassNode in,
				CompilationUnitDeclaration parent, TransformContext context) {

		}

	}

	static class MethodTransformer implements
			TransformPart<MethodNode, MethodDeclaration, ClassDeclaration> {
		VisualCodeBuilder builder;

		public MethodTransformer(VisualCodeBuilder builder) {
			this.builder = builder;
		}

		@Override
		public MethodDeclaration transform(MethodNode obj,
				ClassDeclaration parent, TransformContext context) {
			MethodDeclaration decl = builder.declareMethod(parent, new Type(obj
					.getReturnType().getName()), obj.getName());
			return decl;
		}

		@Override
		public Class<MethodNode> getAcceptedType() {
			return MethodNode.class;
		}

		@Override
		public Class<ClassDeclaration> getParentType() {
			return ClassDeclaration.class;
		}

		@Override
		public void postTransform(MethodDeclaration obj, MethodNode in,
				ClassDeclaration parent, TransformContext context) {

		}

	}

	@GroovyASTTransformation
	public static class CompositeASTTransformation implements ASTTransformation {
		private CompositeTransformingVisitorSupport support;

		public CompositeASTTransformation(
				CompositeTransformingVisitorSupport support) {
			this.support = support;
		}

		@Override
		public void visit(ASTNode[] nodes, SourceUnit source) {
			support.visitModuleNode(source.getAST());
		}

	}

	@Test
	public void simpleTransform() {
		VisualCodeBuilder_Impl builder = new VisualCodeBuilder_Impl();
		CompositeTransformingVisitorSupport support = new CompositeTransformingVisitorSupport(
				null, new ClassTransformer(builder), new MethodTransformer(
						builder), new ModuleTransformer(builder));
		CompositeASTTransformation transform = new CompositeASTTransformation(
				support);
		String script = "class X { public int foo(int param) { return param+1; } }";
		CompilerConfiguration conf = new CompilerConfiguration();
		conf.addCompilationCustomizers(new ASTTransformationCustomizer(
				transform));
		GroovyShell shell = new GroovyShell(conf);
		Script foo = shell.parse(script);
		assertNotNull(support.getRoot().getRootObject());

		CompilationUnitDeclaration cu = (CompilationUnitDeclaration) support
				.getRoot().getRootObject();
		assertEquals("X.groovy", cu.getName());

		assertEquals(1, cu.getDeclaredClasses().size());

		ClassDeclaration cls = cu.getDeclaredClasses().get(0);
		assertEquals("TestX", cls.getName());

		assertEquals(4, cls.getDeclaredMethods().size());
		assertEquals("foo", cls.getDeclaredMethods().iterator().next()
				.getName());
	}

	@Test
	public void testModuleNodePart() throws Exception {

		SourceUnit source = fromCode("class A {}");
		CompositeTransformingVisitorSupport visitor = init(source);
		visitor.visitModuleNode(source.getAST());
		assertNotNull(visitor.getRoot().getRootObject());
		CompilationUnitDeclaration decl = (CompilationUnitDeclaration) visitor
				.getRoot().getRootObject();
		assertEquals(1, decl.getDeclaredClasses().size());
	}

	static SourceUnit fromCode(String code) throws Exception {
		SourceUnit sourceUnit = SourceUnit.create("Test.groovy", code);
		CompilationUnit compUnit = new CompilationUnit();
		compUnit.addSource(sourceUnit);
		compUnit.compile(Phases.CANONICALIZATION);
		return sourceUnit;
	}

	static CompositeTransformingVisitorSupport init(SourceUnit sourceUnit)
			throws Exception {
		VisualCodeBuilder_Impl codeBuilder = new VisualCodeBuilder_Impl();
		StateMachine stateMachine = new StateMachine();

		codeBuilder.setIdRequest(new IdRequest() {

			private IdGenerator generator = FlowFactory.newIdGenerator();

			@Override
			public String request() {
				String result = generator.newId();
				return result;
			}
		});

		Reader in = sourceUnit.getSource().getReader();
		CodeLineColumnMapper mapper = new CodeLineColumnMapper();
		mapper.init(in);

		return new CompositeTransformingVisitorSupport(
				sourceUnit,
				new BinaryExpressionPart(stateMachine, sourceUnit, codeBuilder,
						mapper),
				new BreakPart(stateMachine, sourceUnit, codeBuilder, mapper),
				new ClassNodePart(stateMachine, sourceUnit, codeBuilder, mapper),
				new ContinuePart(stateMachine, sourceUnit, codeBuilder, mapper),
				new DeclarationExpressionPart(stateMachine, sourceUnit,
						codeBuilder, mapper),
				new FieldPart(stateMachine, sourceUnit, codeBuilder, mapper),
				new ForLoopPart(stateMachine, sourceUnit, codeBuilder, mapper),
				new IfStatementPart(stateMachine, sourceUnit, codeBuilder,
						mapper),
				new MethodNodePart(stateMachine, sourceUnit, codeBuilder,
						mapper),
				new ModuleNodePart(codeBuilder),
				new PostFixExpressionPart(stateMachine, sourceUnit,
						codeBuilder, mapper),
				new ReturnStatementPart(stateMachine, sourceUnit, codeBuilder,
						mapper),
				new WhileLoopPart(stateMachine, sourceUnit, codeBuilder, mapper));

	}
}