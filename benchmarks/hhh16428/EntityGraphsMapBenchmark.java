package org.hibernate.graph;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.hibernate.SessionFactory;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Inheritance;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Subgraph;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;

/** Temporary HHH-16428 evidence harness, not an upstream regression test. */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
@SuppressWarnings({"rawtypes", "unchecked"})
public class EntityGraphsMapBenchmark {
	@Benchmark
	public boolean mapEquality(MapState state) {
		return EntityGraphs.areEqual(state.first, state.second);
	}

	@Benchmark
	public boolean rootGraphEquality(RootState state) {
		return EntityGraphs.areEqual(state.first, state.second);
	}

	public static class Model {
		StandardServiceRegistry registry;
		SessionFactory factory;
		static final Class[] TYPES = {Base.class, Sub1.class, Sub2.class, Sub3.class, Sub4.class, Sub5.class, Sub6.class, Sub7.class, Sub8.class, Sub9.class, Sub10.class, Sub11.class, Sub12.class, Sub13.class, Sub14.class, Sub15.class, Sub16.class};

		void start() {
			registry = new StandardServiceRegistryBuilder()
					.applySetting("hibernate.connection.url", "jdbc:h2:mem:hhh16428")
					.applySetting("hibernate.hbm2ddl.auto", "none")
					.applySetting("hibernate.show_sql", "false")
					.build();
			MetadataSources sources = new MetadataSources(registry);
			for (Class type : TYPES) {
				sources.addAnnotatedClass(type);
			}
			factory = sources.buildMetadata().buildSessionFactory();
		}

		Subgraph createSubgraph(Class type, String attribute) {
			RootGraph<Base> root = factory.createEntityGraph(Base.class);
			Subgraph subgraph = root.addSubgraph("parent", type);
			subgraph.addAttributeNodes(attribute);
			return subgraph;
		}

		void close() {
			if (factory != null) factory.close();
			StandardServiceRegistryBuilder.destroy(registry);
		}
	}

	@State(Scope.Thread)
	public static class MapState extends Model {
		@Param({"EMPTY", "EQUAL_1", "EQUAL_4", "EQUAL_16",
				"KEY_MISMATCH_1", "KEY_MISMATCH_4", "KEY_MISMATCH_16",
				"VALUE_MISMATCH_1", "VALUE_MISMATCH_4", "VALUE_MISMATCH_16"})
		public String caseName;
		Map<Class, Subgraph> first;
		Map<Class, Subgraph> second;

		@Setup(Level.Trial)
		public void setup() {
			start();
			first = new HashMap<>();
			second = new HashMap<>();
			int size = caseName.equals("EMPTY") ? 0 : Integer.parseInt(caseName.substring(caseName.lastIndexOf('_') + 1));
			for (int i = 0; i < size; i++) {
				first.put(TYPES[i], createSubgraph(TYPES[i], "name"));
				second.put(TYPES[i], createSubgraph(TYPES[i], "name"));
			}
			if (caseName.startsWith("KEY_MISMATCH")) {
				second.remove(TYPES[0]);
				second.put(TYPES[16], createSubgraph(TYPES[16], "name"));
			}
			if (caseName.startsWith("VALUE_MISMATCH")) {
				// Pick the final key in this concrete fixture's actual traversal;
				// do not assume Class hashes or HashMap insertion order are stable.
				Class mismatch = null;
				for (Class key : first.keySet()) mismatch = key;
				second.put(mismatch, createSubgraph(mismatch, "other"));
			}
			boolean expected = !caseName.contains("MISMATCH");
			if (EntityGraphs.areEqual(first, second) != expected) throw new IllegalStateException("Invalid fixture " + caseName);
			System.out.println("Fixture " + caseName + " first-key-order=" + first.keySet());
		}

		@TearDown(Level.Trial)
		public void teardown() { close(); }
	}

	@State(Scope.Thread)
	public static class RootState extends Model {
		@Param({"1", "4", "16"})
		public int size;
		RootGraph<Base> first;
		RootGraph<Base> second;

		@Setup(Level.Trial)
		public void setup() {
			start();
			first = factory.createEntityGraph(Base.class);
			second = factory.createEntityGraph(Base.class);
			for (int i = 0; i < size; i++) {
				first.addSubgraph("parent", TYPES[i]).addAttributeNodes("name");
				second.addSubgraph("parent", TYPES[i]).addAttributeNodes("name");
			}
			if (first.getAttributeNodes().size() != 1 || second.getAttributeNodes().size() != 1
					|| first.getAttributeNodes().get(0).getSubgraphs().size() != size
					|| second.getAttributeNodes().get(0).getSubgraphs().size() != size) {
				throw new IllegalStateException("Unexpected root subgraph count " + size);
			}
			if (!EntityGraphs.areEqual(first, second)) throw new IllegalStateException("Invalid equal root fixture");
		}

		@TearDown(Level.Trial)
		public void teardown() { close(); }
	}

	@Entity(name = "BenchmarkBase")
	@Inheritance
	public static class Base {
		@Id public Long id;
		public String name;
		public String other;
		@ManyToOne public Base parent;
	}
	@Entity(name = "BenchmarkSub1") public static class Sub1 extends Base {}
	@Entity(name = "BenchmarkSub2") public static class Sub2 extends Base {}
	@Entity(name = "BenchmarkSub3") public static class Sub3 extends Base {}
	@Entity(name = "BenchmarkSub4") public static class Sub4 extends Base {}
	@Entity(name = "BenchmarkSub5") public static class Sub5 extends Base {}
	@Entity(name = "BenchmarkSub6") public static class Sub6 extends Base {}
	@Entity(name = "BenchmarkSub7") public static class Sub7 extends Base {}
	@Entity(name = "BenchmarkSub8") public static class Sub8 extends Base {}
	@Entity(name = "BenchmarkSub9") public static class Sub9 extends Base {}
	@Entity(name = "BenchmarkSub10") public static class Sub10 extends Base {}
	@Entity(name = "BenchmarkSub11") public static class Sub11 extends Base {}
	@Entity(name = "BenchmarkSub12") public static class Sub12 extends Base {}
	@Entity(name = "BenchmarkSub13") public static class Sub13 extends Base {}
	@Entity(name = "BenchmarkSub14") public static class Sub14 extends Base {}
	@Entity(name = "BenchmarkSub15") public static class Sub15 extends Base {}
	@Entity(name = "BenchmarkSub16") public static class Sub16 extends Base {}
}
