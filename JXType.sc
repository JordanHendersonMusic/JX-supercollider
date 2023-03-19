JXTypeError : Error {
	var <>topmethod, <>expected, <>got, <>va;
	*new {|topmethod, expected, got, v|
		//would cause a loop!// JXTypeCheck(topmethod -> Method, expected -> [Class, [Class]], got -> Class);
		^super.new().topmethod_(topmethod).expected_(expected).got_(got).va_(v);
	}
	errorString {
		^
		"\nType Error: \"%:%\""
		"\n  Expected: \"%\""
		"\n       Got: \"%\""
		"\nWith value: \"%\""
		"\n   In file: \"%\""
		.format(
			topmethod.ownerClass,
			topmethod.name,

			[expected].flatten.reduce({|l,r| l.asString ++ " or " ++ r.asString }),
			got,
			va,
			topmethod.filenameSymbol
		)
	}
	reportError {
		this.errorString.postln;
		if(protectedBacktrace.notNil, { this.postProtectedBacktrace });
		this.dumpBackTrace;

		"\n\n\n^^ The preceding error dump is for: ".postln;
		this.errorString.error
	}
}

JXAssertError : Error {
	var <>topmethod, <>msg;
	*new {|topmethod, msg|
		//would cause a loop!// JXTypeCheck(topmethod -> Method, expected -> [Class, [Class]], got -> Class);
		^super.new().topmethod_(topmethod).msg_(msg);
	}
	errorString {
		^
		"\nAssert Error: \"%:%\""
		"\n     In file: \"%\""
		"\n         Msg: \"%\""
		.format(
			topmethod.ownerClass,
			topmethod.name,
			topmethod.filenameSymbol,
			msg
		)
	}
	reportError {
		this.errorString.postln;
		if(protectedBacktrace.notNil, { this.postProtectedBacktrace });
		this.dumpBackTrace;

		"\n\n\n^^ The preceding assert dump is for: ".postln;
		this.errorString.error
	}
}




JXTypeCheck {
	*new { |method ...assocs|
		if(method.isKindOf(Method).not,
			{^Error("JXTypeCheck must have thisMethod as first argument").throw});

		^assocs.collect({ |a|
			if( JXTypeCheck.prMatch(a.key, a.value),
				true,
				{ ^JXTypeCheck.prError(method, a.key, a.value).throw }
			)
		}).reduce('&&')
	}
	*sc { |...assocs|
		^assocs.collect({ |a|
			if( JXTypeCheck.prMatch(a.key, a.value),
				true,
				{ ^JXTypeCheck.prError(nil, a.key, a.value).throw }
			)
		}).reduce('&&')
	}

	*prMatch {|obj, classes|
		^[classes].flatten.collect({ |c| obj.isKindOf(c) }).reduce('||')
	}

	*prError {|method, obj, cs|
		^JXTypeError(method ? thisMethod, cs, obj.class, obj);
	}
}

JXTypeQuery {
	*new { |...assocs| ^try { JXTypeCheck(thisMethod, *assocs) } { false } }
}
JXAssert {
	*new {|topmethod, bool, msg|
		JXTypeCheck(thisMethod, topmethod -> Method);
		JXTypeCheck(thisMethod, bool -> Boolean, msg -> [Symbol, String]);
		^bool.not.if({JXAssertError(topmethod, msg.asSymbol).throw}, {true});
	}
}
JXInheritsCheck {
	*new {|...assocs|
		^assocs.collect({ |a|
			if( JXInheritsCheck.prMatch(a.key, a.value),
				true,
				{ ^JXInheritsCheck.prError(a.key, a.value).throw }
			)
		}).reduce('&&')
	}
	*prMatch {|obj, classes|
		^[classes].flatten.collect({ |c| obj.isKindOfClass(c) }).reduce('||')
	}
	*prError {|obj, cs|
		^Error("Unexpected class, expected %, got % with value %"
			.format([cs].flatten.reduce({|l,r|
				l.asString ++ " or " ++ r.asString
			}), obj.class, obj)
		)
	}
}

JXInheritsQuery {
	*new { |...assocs| ^try { JXInheritsCheck(*assocs) } { false } }
}


JXAssertOpenedFromDirectory {
	*new {|dir|
		JXAssert(thisMethod, File.getcwd == dir,
			"Must open this project by double clicking on the file, do not open supercollider first!"
			"\n\nExpected \"%\" \nGot \"%\""
			.format(dir, File.getcwd)
		);
	}
}



