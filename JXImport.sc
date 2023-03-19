JXImport {
	*new { |path|
		var out;
		Environment.make({
			out = thisProcess.interpreter.compileFile(path).()
		});
		^out
	}
	*cwd { |path| ^JXImport(File.getcwd +/+ path) }
}
