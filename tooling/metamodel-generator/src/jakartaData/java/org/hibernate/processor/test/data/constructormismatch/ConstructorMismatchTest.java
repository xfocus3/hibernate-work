package org.hibernate.processor.test.data.constructormismatch;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import javax.tools.ToolProvider;

import org.hibernate.processor.HibernateProcessor;
import org.hibernate.processor.test.util.CompilationTest;
import org.hibernate.processor.test.util.TestUtil;
import org.junit.jupiter.api.Test;

import static java.nio.charset.Charset.defaultCharset;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@CompilationTest
class ConstructorMismatchTest {
	@Test
	void reportsSelectionAndConstructorTypes() throws Exception {
		final var diagnostics = new DiagnosticCollector<JavaFileObject>();
		assertFalse( compile( InvalidSummaryRepository.class, diagnostics ) );
		final String message = diagnostics.getDiagnostics().stream()
				.filter( diagnostic -> diagnostic.getKind() == Diagnostic.Kind.ERROR )
				.map( diagnostic -> diagnostic.getMessage( Locale.ROOT ) )
				.filter( diagnostic -> diagnostic.contains( "has no constructor matching query selection list" ) )
				.collect( Collectors.joining( "\n" ) );
		assertFalse( message.isEmpty(), diagnostics.getDiagnostics().toString() );
		assertTrue( message.contains( "java.lang.String" ) && message.contains( "[]" )
				&& message.contains( "int" ), "Missing selection and constructor parameter types: " + message );
	}

	@Test
	void matchingConstructorStillCompiles() throws Exception {
		final var diagnostics = new DiagnosticCollector<JavaFileObject>();
		assertTrue( compile( ValidSummaryRepository.class, diagnostics ), diagnostics.getDiagnostics().toString() );
	}

	private boolean compile(Class<?> repository, DiagnosticCollector<JavaFileObject> diagnostics) throws Exception {
		final var compiler = ToolProvider.getSystemJavaCompiler();
		try ( var fileManager = compiler.getStandardFileManager( diagnostics, Locale.ROOT, defaultCharset() ) ) {
			final var sourceFiles = List.of(
					TestUtil.getSourceFile( Book.class ),
					TestUtil.getSourceFile( Summary.class ),
					TestUtil.getSourceFile( repository )
			);
			return compiler.getTask(
					null, fileManager, diagnostics,
					List.of( "-d", TestUtil.getOutBaseDir( ConstructorMismatchTest.class ).getAbsolutePath(),
							"-processor", HibernateProcessor.class.getName() ),
					null, fileManager.getJavaFileObjectsFromFiles( sourceFiles )
			).call();
		}
	}
}
