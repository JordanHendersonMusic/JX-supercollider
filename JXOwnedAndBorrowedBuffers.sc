JXFnBase : AbstractFunction {
	var func;
	*new { |f|
		JXTypeCheck(thisMethod, f -> [AbstractFunction]);
		^super.newCopyArgs(f);
	}
	value{ |...args| ^func.(*args) }
	* {|other| ^JXFnCompose(this, other) }
}


JXFnOp {
	*new { |f|
		^JXFnBase({|x ...y| x.perform(f, *y) })
	}
}
// returns a constant regardless of input
JXFnConst {
	*new {|valueToReturn| ^JXFnBase({ valueToReturn }) }
}
// returns the input - K combinator
JXFnIdent {
	*new { ^JXFnBase({|a| a }) }
}
// S combinator
JXFnHook {
	*new { |f, g|
		^JXFnBase({ |x| f.(x, g.(x)) })
	}
}
// B combinator
JXFnCompose {
	*new {|...fns|
		^JXFnBase({|...i| fns.reverse.inject(i, {|prev, f| f.(*prev)}) })
	}
}
// C n cominator
JXFnFlip {
	*new {|f| ^JXFnBase({|...args| f.(*args.reverse) }) }
}
// W combinator
JXFnJoin {
	*new {|f| ^JXFnBase({|x| f.(x, x) }) }
}
// psi n
JXFnPsi {
	*new { |f, g|
		^JXFnBase({|...args| f.( *args.collect(g) ) })
	}
}
// phi n
JXFnPhi {
	*new { |f, g, h|
		^JXFnBase({|...x| g.(f.(*x), h.(*x)) })
	}
}
JXFnD {
	*new { |f, g|
		^JXFnBase({|x, y| f.(x, g.(y)) })
	}
}
JXFnApplyR {
	*new {|f ...v|
		^JXFnBase({|...x| f.(*(x ++ v)) })
	}
}
JXFnApplyL {
	*new {|f ...v|
		^JXFnBase({|...x| f.(*(v ++ x)) })
	}
}



JXBufferGenerator { value { |numChannels, numFrames| } }
JXBufferGeneratorFromFile : JXBufferGenerator {
	var path;
	*new{|path|
		JXTypeCheck(thisMethod, path -> String);
		^super.newCopyArgs(path)
	}
	value { |numChannels, numFrames|
		var buffer = Buffer.read(Server.default, path);
		JXAssert(this, buffer.numChannels == numChannels,
			"buffer file : \"%\" does not match the specified number of channels : \"%\""
			.format(path, numChannels)
		);
		JXAssert(this, buffer.numFrames == numFrames,
			"buffer file : \"%\" does not match the specified number of frames : \"%\""
			.format(path, numFrames)
		);
		^buffer
	}
}
JXBufferGeneratorFill : JXBufferGenerator {
	var func;
	*new{|func|
		^super.newCopyArgs(func)
	}
	value { |numChannels, numFrames|
		var check = JXTypeCheck(thisMethod,
			numChannels -> Integer, numFrames -> Integer
		);
		var a = (numFrames*numChannels).collect{|i|
			var frame = i.mod(numFrames);
			var lerp = frame / numFrames;
			var channel = (i / numFrames).floor;
			func.(frame, lerp, channel)
		};
		^Buffer.loadCollection(Server.default, a, numChannels);
	}
}



JXOwnedBuffer : JXResourceUgen {
	// function is given the frame number as the first afrgument,
	//   and a lerp factor as the second, and channel number as third
	*fill { |name, numChannels, duration, func|
		var check = JXTypeCheck(
			thisMethod,
			name -> Symbol,
			numChannels -> Integer,
			duration -> Number
		);
		^JXOwnedBuffer.prBase(name, numChannels, duration, JXBufferGeneratorFill(func));
	}

	*fromFile { |name, numChannels, duration, file|
		var check = JXTypeCheck(
			thisMethod,
			name -> Symbol,
			numChannels -> Integer,
			duration -> Number,
			file -> String
		);
		^JXOwnedBuffer.prBase(name, numChannels, duration, JXBufferGeneratorFromFile.(file));
	}
	*prBase { |name, numChannels, duration, gen|
		var check = JXTypeCheck(
			thisMethod,
			name -> Symbol,
			numChannels -> Integer,
			duration -> Number,
			gen -> JXBufferGenerator
		);
		var busSelectorName = JXResourceUgen.createSafeServerControlName(name);
		JXOwnedBuffer.registerConstructor(JXOwnedBufferConstructor(
			name,
			busSelectorName,
			(duration * Server.default.sampleRate).ceil.asInteger,
			numChannels,
			gen
		));
		^busSelectorName.kr(-1);
	}
}

JXOwnedBufferConstructor : JXResourceConstructor {
	var <busSelectorName, <numFrames, <numChannels, <bufferGenerator;
	*new {|name, busSelectorName, numFrames, channels, gen|
		JXTypeCheck(thisMethod,
			name -> Symbol,
			busSelectorName -> Symbol,
			numFrames -> Integer,
			channels -> Integer,
			gen -> JXBufferGenerator
		);
		^super.newCopyArgs(name, busSelectorName, numFrames, channels, gen)
	}
	createResource { |oscAddr|
		var b = bufferGenerator.(numChannels, numFrames);
		Server.default.sync;
		^JXOwnedBufferResource(name, this, b)
	}
}

JXOwnedBufferResource : JXResource {
	var <buffer;
	*new { |name, constructor, buffer|
		JXTypeCheck(thisMethod,
			name -> Symbol,
			constructor -> JXOwnedBufferConstructor,
			buffer -> Buffer
		);
		^super.newCopyArgs(name, constructor, buffer)
	}
	getArg { ^[this.constructor.busSelectorName, this.buffer.bufnum] }
}


JXBorrowedBuffer : JXResourceUgen {
	*new { |name, numChannels|
		var check = JXTypeCheck(thisMethod, name -> Symbol, numChannels -> Integer);
		var busSelectorName = JXResourceUgen.createSafeServerControlName(name);
		JXBorrowedBuffer.registerConstructor(
			JXBorrowedBufferConstructor(name, busSelectorName, numChannels)
		);
		^busSelectorName.kr(-1);
	}

}
JXBorrowedBufferConstructor : JXResourceConstructor {
	var <busSelectorName, <numChannels, <>hasBeenSet;
	*new {|name, busSelectorName, numChannels|
		JXTypeCheck(thisMethod, name -> Symbol, busSelectorName -> Symbol, numChannels -> Integer);
		^super.newCopyArgs(name, busSelectorName, numChannels, false)
	}
	createResource { |oscAddr|
		^JXBorrowedBufferResource(name, this);
	}
}

JXBorrowedBufferResource : JXResource {
	connect { |buffer, thisSynth|
		JXTypeCheck(thisMethod, buffer -> Buffer, thisSynth -> JXSynth);
		JXAssert(thisMethod, this.constructor.hasBeenSet.not,
			'Buffer was already set, cannot do this twice');
		JXAssert(thisMethod, buffer.numChannels == this.constructor.numChannels,
			"Channel mismatch when connecting from %  to %"
			.format([buffer.numChannels, buffer], [this.constructor.numChannels, this.name]));
		thisSynth.set(this.constructor.busSelectorName, buffer.bufnum);
		this.constructor.hasBeenSet = true;
	}
}

JXConnection_pr_OwnedBuffer2BorrowedBuffer : JXResourceConnectionFunctionBase {
	*initClass { JXResourceConnectionFunctionBase.register(
		JXOwnedBufferResource, JXBorrowedBufferResource,
		JXConnection_pr_OwnedBuffer2BorrowedBuffer
	) }
	*makeConnectionOnly { |lhs, rhs|
		JXTypeCheck(thisMethod,
			lhs -> JXOwnedResourcePair, rhs -> JXOwnedResourcePair
		);
		JXTypeCheck(thisMethod,
			lhs.resource -> JXOwnedBufferResource, rhs.resource -> JXBorrowedBufferResource
		);
		JXTypeCheck(thisMethod, lhs.owner -> JXSynth); // only synths can own busses

		rhs.resource.connect(lhs.resource.buffer, rhs.owner)
	}

	*postConnectionCleanup { |lhs, rhs|
		JXTypeCheck(thisMethod,
			lhs -> JXOwnedResourcePair, rhs -> JXOwnedResourcePair
		);
		JXTypeCheck(thisMethod,
			lhs.resource -> JXOwnedBufferResource, rhs.resource -> JXBorrowedBufferResource
		);
	}
}
