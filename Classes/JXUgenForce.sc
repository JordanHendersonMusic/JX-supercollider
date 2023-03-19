JXForceRate {
	*new{|toRate, fromRate, sig|
		JXTypeCheck(thisMethod,
			toRate -> JXRateSymbol,
			fromRate -> JXRateSymbol
		);
		if(toRate == fromRate, { ^sig });
		^case
		{toRate.isAudio && fromRate.isControl} { K2A.ar(sig) }
		{toRate.isControl && fromRate.isAudio} { A2K.kr(sig) }
		{Error(
			"rates do not match, something is wrong, to %, from %"
			.format(toRate, fromRate)).throw
		}
	}
}

