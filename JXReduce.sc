// defines how many2one mappings should combine
JXReduce {
	*mean { ^JXReduceMean() }
	*sum { ^JXReduceSum() }
	value {^this.subclassResponsibility(thisMethod) }
}

JXCustomReduction : JXReduce {
	var func;
	*new {|func|
		JXTypeCheck(thisMethod, func -> Function);
		^super.newCopyArgs(func)
	}
	value { |ar| func.(ar) }
}
JXReduceMean : JXReduce {
	value { |ar| ^ar.mean }
}
JXReduceSum  : JXReduce {
	value { |ar| ^ar.sum }
}






JXReshape {
	classvar requiredArgNames;
	classvar store;

	*initClass {
		store = ();
		requiredArgNames = Array[\toChans, \fromChans, \jxRateSym, \sig]
	}

	*register {|name, reshaper|
		JXTypeCheck(name -> Symbol, reshaper -> JXReshape);
		JXAssert(thisMethod, store.includesKey(name).not,
			"JXReshape cannot be registered as key \"%\" already exists"
			.format(name)
		);
		store[name] = reshaper;
	}
	*sliceNpad { ^JXReshapeSliceNPad() }
	*circular { ^JXReshapeCircular() }
	*flat { ^JXReshapeFlat() }
	*custom {|name|
		JXTypeCheck(name -> Symbol);
		JXAssert(thisMethod, store.includesKey(name),
			"XReshape cannot find key: %"
			.format(name)
		);
		^store[name]
	}

	ar { |toChans, sig|
		JXAssert(thisMethod, sig.rate == \audio,
			"Signal must be audio rate for an \"ar\" ugen"
		);
		^this.value(toChans, sig.numChannels, JXRateSymbol(\ar), sig)
	}
	kr { |toChans, sig|
		JXAssert(thisMethod, sig.rate == \control,
			"Signal must be control rate for a \"kr\" ugen"
		);
		^this.value(toChans, sig.numChannels, JXRateSymbol(\kr), sig)
	}
	value { |toChans, fromChans, jxRateSym, sig|
		JXTypeCheck(thisMethod,
			toChans -> Integer,
			fromChans -> Integer,
			jxRateSym -> JXRateSymbol
		);
		^this.subclassResponsibility(thisMethod)
	}
}



// cut of exessive channels, and pads with zero for lacking channels
JXReshapeSliceNPad : JXReshape {
	value { |toChans, fromChans, jxRateSym, sig|
		^case
		{toChans == fromChans}
		{sig}

		{toChans < fromChans}
		{ sig[0..toChans] }

		{toChans > fromChans}
		{ (sig ++ jxRateSym.performOn(DC, 0).dup(toChans - fromChans)).flatten }
	}

}

JXReshapeCircular : JXReshape {
	value { |toChans, fromChans, jxRateSym, sig|
		^case
		{toChans == fromChans}
		{sig}

		{
			var ambi = sig.collect({|s, n|
				var thetaStep = (2*pi) / fromChans;
				var theta = ( (thetaStep * n) + (pi / 2) ) .wrap(-pi, pi);
				jxRateSym.performOn(PanB2, s, theta)
			}).reduce('+');
			jxRateSym.performOn(DecodeB2, toChans, ambi[0], ambi[1], ambi[2], 0)
		}
	}
}

JXReshapeFlat : JXReshape {
	value { |toChans, fromChans, jxRateSym, sig|
		^case
		{toChans == fromChans}
		{sig}

		{ jxRateSym.performOn(JXLineSpreader, toChans, sig) }
	}
}


JXReshapeDef : JXReshape {
	var func;
	*new { |name, func|
		var c = JXTypeCheck(thisMethod, name -> Symbol, func -> Function);
		var a = JXAssert(thisMethod, func.def.argNames == requiredArgNames,
			"The function provided to JXReshapeCustom must have the following argument names "
			"in the exact same order: %".format(JXReshape.requiredArgNames)
		);
		var self = super.newCopyArgs(func);
		JXReshape.register(name, self);
	}

	value {
		|toChans, fromChans, jxRateSym, sig|
		JXTypeCheck(thisMethod,
			toChans -> Integer,
			fromChans -> Integer,
			jxRateSym -> JXRateSymbol
		);
		^func.(toChans, fromChans, jxRateSym, sig)
	}
}
