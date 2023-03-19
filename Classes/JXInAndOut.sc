// turns \ar, \kr .. etc, into a type
JXRateSymbol {
	var <state;
	*new {|sym|
		JXTypeCheck(thisMethod, sym -> Symbol);
		^case
		{[\ar, \kr, \ir, \tr].includes(sym)}
		{super.newCopyArgs(sym)}

		{sym == \audio}
		{super.newCopyArgs(\ar)}

		{sym == \control}
		{super.newCopyArgs(\kr)}

		{sym == \trigger}
		{super.newCopyArgs(\tr)}

		{Error("input is not a valid rate, input: %".format(sym)).throw}
	}
	isAudio { ^state == \ar }
	isControl { ^state == \kr }
	isTrigger { ^state == \tr }
	isInit { ^state == \ir }
	asString { ^state.asString }
	== { |other| ^state == other.state }
	!= { |other| ^(this == other).not }
	performOn {|class ...args|
		JXTypeCheck(thisMethod, class -> Class);
		^class.perform(state, *args)
	}
	asLongRate {
		^if( [\ar, \kr].includes(state),
			{switch(state, \ar, \audio, \kr, \control)},
			{Error("state is not \ar nor \kr, state: %".format(state)).throw})
	}
}






JXOut : JXResourceUgen {
	*ar {|name, value| ^JXOut.prRegister(JXRateSymbol(\ar), name, value) }
	*kr {|name, value| ^JXOut.prRegister(JXRateSymbol(\kr), name, value) }

	*prRegister {|rate, name, value|
		var check = JXTypeCheck(thisMethod, rate -> JXRateSymbol, name -> Symbol);
		var busSelectorName = JXResourceUgen.createSafeServerControlName(name);

		JXAssert(thisMethod, rate.asLongRate == value.rate, 'wrong rate input');
		JXAssert(thisMethod, value.asArray.shape.size == 1, "the array in JXOut must be flat: %".format(value));

		JXOut.registerConstructor(JXOutConstructor(
			name, value.numChannels, rate, busSelectorName
		));

		^rate.performOn(ReplaceOut, busSelectorName.asSymbol.kr(999), value);
	}
}

JXOutConstructor : JXResourceConstructor {
	var <numChannels, <rate, <busSelectorName;
	*new { |name, numChannels, rate, busSelectorName|
		JXTypeCheck(thisMethod, name -> Symbol, busSelectorName -> Symbol,
			numChannels -> Integer, rate -> JXRateSymbol
		);
		^super.newCopyArgs(name, numChannels, rate, busSelectorName);
	}
	createResource { |oscAddr|
		^JXOutResource(name, this, Bus.alloc(rate.asLongRate, Server.default, numChannels))
	}
}

JXOutResource : JXResource {
	var <bus;
	*new { |name, constructor, bus|
		JXTypeCheck(thisMethod, name -> Symbol, constructor -> JXOutConstructor, bus -> Bus);
		^super.newCopyArgs(name, constructor, bus);
	}
	getArg { ^[this.constructor.busSelectorName, this.bus] }
	asUgen { |d| ^this.prOutUgen(d) }

	prInUgen { ^this.constructor.rate.performOn(In, bus, this.constructor.numChannels)}
	prOutUgen { |d|
		JXAssert(thisMethod, d.asArray.flatten.size == this.constructor.numChannels, 'Channel mismatch');
		^this.constructor.rate.performOn(Out, this.bus, d.asArray.flatten)
	}
}









JXIn : JXResourceUgen {
	*ar { |name, numChannels, reduceMode, reshapeMode|
		^JXIn.prRegister(JXRateSymbol(\ar), name, numChannels,
			reduceMode ? JXReduce.sum, reshapeMode ? JXReshape.circular)
	}
	*kr { |name, numChannels, reduceMode, reshapeMode|
		^JXIn.prRegister(JXRateSymbol(\kr), name, numChannels,
			reduceMode ? JXReduce.mean, reshapeMode ? JXReshape.circular)
	}
	*prRegister { |rate, name, numChannels, reduceMode, reshapeMode|
		var c = JXTypeCheck(thisMethod,
			rate -> JXRateSymbol, name -> Symbol, numChannels -> Integer,
			reduceMode -> JXReduce, reshapeMode -> JXReshape
		);
		var controlname = JXResourceUgen.createSafeServerControlName(name);
		JXIn.registerConstructor(JXInConstructor(
			name, numChannels, rate, reduceMode, reshapeMode, controlname
		));
		^rate.performOn(NamedControl, controlname, 0!numChannels);
	}
}

JXInConstructor : JXResourceConstructor {
	var <numChannels, <rate, <reduceMode, <reshapeMode, <controlName;
	*new {|name, numChannels, rate, reduceMode, reshapeMode, controlName|
		JXTypeCheck(thisMethod,
			name -> Symbol, numChannels -> Integer,
			rate -> JXRateSymbol, reduceMode -> JXReduce, reshapeMode -> JXReshape, controlName -> Symbol
		);
		^super.newCopyArgs(name, numChannels, rate, reduceMode, reshapeMode, controlName)
	}
	createResource { |oscAddr|
		^JXInResource(
			name, this,
			Bus.alloc(rate.asLongRate, Server.default, numChannels)
		)
	}
}

JXInResource : JXResource {
	classvar uidOfMapper = 0;
	var <bus;
	var connectionsTo       /*List[ [JXOutResource, remapFunc] ]*/;
	var remakeMapperToggle  /*Boolean*/;
	var mapperSynth         /*[Synth, Nil]*/;

	*new {|name, constructor, bus|
		JXTypeCheck(thisMethod, name -> Symbol, constructor -> JXInConstructor, bus -> Bus);
		^super.newCopyArgs(name, constructor, bus, List[], false, nil)
	}
	// sometimes args map do no work on audio rate buses, eh?
	getArg { ^[this.constructor.controlName, this.bus.asMap] }
	getMapArg { ^[this.constructor.controlName, this.bus] }

	addConnectionFromOut {|o, mapFunc|
		JXTypeCheck(thisMethod, o -> JXOutResource);
		connectionsTo.add([o, mapFunc ?? JXFnIdent()]);
		remakeMapperToggle = true;
	}

	remakeMapper { |parent, oscAddr|
		JXTypeCheck(thisMethod, parent.asTarget -> Node, oscAddr -> Symbol);
		if(remakeMapperToggle.not, {^nil}, {remakeMapperToggle = false});
		if (connectionsTo.size == 0, {^nil});
		try {mapperSynth.free} {};

		mapperSynth = SynthDef(
			JXSynthDef.prSynthDefNameSanitize(JXInResource.prSynthName((oscAddr.asString ++ "/" ++ name).asSymbol)),
			{
				var array = connectionsTo.asArray.collect({|ar|
					ar[1].( this.prSafePortIn(ar[0]) )
				});
				var reduced = this.constructor.reduceMode.(array);
				this.prOutUgen(reduced);
			}
		).play(target: parent.asTarget, addAction: \addBefore);
	}

	asUgen { ^this.prInUgen }
	asPattern {
		JXAssert(thisMethod, this.constructor.rate == JXRateSymbol(\kr),
			"Only kr in resources can be converted to a pattern"
		);
		^Pfunc({ bus.getSynchronous })
	}
	prInUgen { ^this.constructor.rate.performOn(In, bus, this.constructor.numChannels)}
	prOutUgen {|d|
		JXAssert(thisMethod, d.asArray.flatten.size == this.constructor.numChannels, 'Channel mismatch');
		^this.constructor.rate.performOn(ReplaceOut, this.bus, d.asArray.flatten)
	}

	prSafePortIn {|other|
		JXTypeCheck(thisMethod, other -> JXOutResource);
		^this.constructor.reshapeMode.(
			this.constructor.numChannels,
			other.constructor.numChannels,
			this.constructor.rate,
			JXForceRate(this.constructor.rate, other.constructor.rate, [other.prInUgen()].flatten)
		).asArray
	}
	*prSynthName {|oscAddr|
		JXTypeCheck(thisMethod, oscAddr -> Symbol);
		uidOfMapper = uidOfMapper + 1;
		^(oscAddr ++ '---connector---' ++ uidOfMapper.asString).asSymbol
	}
}


JXConnection_pr_Out2In : JXResourceConnectionFunctionBase {
	*initClass {
		JXResourceConnectionFunctionBase.register(
			JXOutResource, JXInResource,
			JXConnection_pr_Out2In
		);
	}
	*makeConnectionOnly { |out, in, optMapFunc|
		JXTypeCheck(thisMethod, out -> JXOwnedResourcePair, in -> JXOwnedResourcePair, optMapFunc -> [Function, JXFnBase, Nil]);
		JXTypeCheck(thisMethod, out.resource -> JXOutResource, in.resource -> JXInResource);
		in.resource.addConnectionFromOut(out.resource, optMapFunc);
	}
	*postConnectionCleanup { |out, in, optMapFunc|
		JXTypeCheck(thisMethod, out -> JXOwnedResourcePair, in -> JXOwnedResourcePair, optMapFunc -> [Function, JXFnBase, Nil]);
		JXTypeCheck(thisMethod, out.resource -> JXOutResource, in.resource -> JXInResource);
		in.resource.remakeMapper(in.owner, in.owner.oscAddr)
	}
}
