package sharpen.core;

import java.io.IOException;
import java.io.Writer;
import java.util.HashMap;
import java.util.Map;

import sharpen.core.csharp.CSharpPrinter;
import sharpen.core.csharp.ast.CSCompilationUnit;

import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.CompilationUnit;

public class Converter {

	private CSharpPrinter _printer;
	protected ICompilationUnit _source;
	protected Writer _writer;
	protected final Configuration _configuration;
	private ASTResolver _resolver;

	public Converter(Configuration configuration) {
		_configuration = configuration;
	}

	public void setSource(ICompilationUnit source) {
		_source = source;
	}

	public void setTargetWriter(Writer writer) {
		_writer = writer;
	}

	public Writer getTargetWriter() {
		return _writer;
	}

	public void setPrinter(CSharpPrinter printer) {
		_printer = printer;
	}

	private CSharpPrinter getPrinter() {
		if (null == _printer) {
			_printer = new CSharpPrinter();
		}
		return _printer;
	}

	public Configuration getConfiguration() {
		return _configuration;
	}

	protected void print(CSCompilationUnit unit) {
		printHeader();
		printTree(unit);
	}

	private void printHeader() {
		try {
			_writer.write(_configuration.header());
		} catch (IOException x) {
			throw new RuntimeException(x);
		}
	}

	private void printTree(CSCompilationUnit unit) {
		CSharpPrinter printer = getPrinter();
		printer.setWriter(_writer);
		printer.print(unit);
	}

	protected CSCompilationUnit run(final CompilationUnit ast) {
		processProblems(ast);
		prepareForConversion(ast);		
		CSCompilationUnit cs = convert(ast);
		if (!cs.ignore()) {
			print(cs);
		}
		return cs;
	}

	protected void processProblems(CompilationUnit ast) {
		if (dumpProblems(ast)) {
			throw new RuntimeException("'" + ASTUtility.compilationUnitPath(ast) + "' has errors, check stderr for details.");
		}
	}

	private CSCompilationUnit convert(final CompilationUnit ast) {
		CSharpBuilder builder = new CSharpBuilder(_configuration);
		builder.setSourceCompilationUnit(ast);
		builder.setASTResolver(_resolver);
		
		builder.run();
		return builder.compilationUnit();
	}
	
	protected boolean dumpProblems(CompilationUnit ast) {
		boolean hasErrors = false;
		for (IProblem problem : ast.getProblems()) {
			if (problem.isError()) {
				dumpProblem(problem);
				hasErrors = true;
			}
		} 
		return ignoringErrors() ? false : hasErrors;
	}

	private boolean ignoringErrors() {
		return _configuration.getIgnoreErrors();
	}

	private void dumpProblem(IProblem problem) {
		System.err.print(problem.getOriginatingFileName());
		System.err.println("(" + problem.getSourceLineNumber() + "): " + problem.getMessage());
	}

	private void prepareForConversion(final CompilationUnit ast) {
		deleteProblemMarkers();
		WarningHandler warningHandler = new WarningHandler() {
			public void warning(ASTNode node, String message) {
				createProblemMarker(ast, node, message);
				System.err.println(getSourcePath() + "(" + ASTUtility.lineNumber(ast, node) + "): " + message);
			}
		};
		_configuration.setWarningHandler(warningHandler);
	}

	private void deleteProblemMarkers() {
		if (createProblemMarkers()) {
			try {
				_source.getCorrespondingResource().deleteMarkers(JavaToCSharp.PROBLEM_MARKER, false, IResource.DEPTH_ONE);
			} catch (CoreException e) {
				e.printStackTrace();
			}
		}
	}

	private void createProblemMarker(CompilationUnit ast, ASTNode node, String message) {
		if (!createProblemMarkers()) {
			return;
		}			
		try {
			IMarker marker = _source.getCorrespondingResource().createMarker(JavaToCSharp.PROBLEM_MARKER);			
			Map<String, Object> attributes = new HashMap<String, Object>();
			attributes.put(IMarker.MESSAGE, message);
			attributes.put(IMarker.CHAR_START, new Integer(node.getStartPosition()));
			attributes.put(IMarker.CHAR_END, new Integer(node.getStartPosition() + node.getLength()));
			attributes.put(IMarker.TRANSIENT, Boolean.TRUE);
			attributes.put(IMarker.SEVERITY, IMarker.SEVERITY_WARNING);
			attributes.put(IMarker.LINE_NUMBER, ASTUtility.lineNumber(ast, node));			
			marker.setAttributes(attributes);			
		} catch (CoreException e) {			
			e.printStackTrace();
		}
	}

	private boolean createProblemMarkers() {
		return _configuration.createProblemMarkers();
	}

	private String getSourcePath() {
		try {
			return _source.getCorrespondingResource().getFullPath().toString();
		} catch (JavaModelException e) {			
			e.printStackTrace();
			return "";
		}
	}
	
	public ASTResolver getASTResolver() {
		return _resolver;
	}

	public void setASTResolver(ASTResolver resolver) {
		_resolver = resolver;
	}
}