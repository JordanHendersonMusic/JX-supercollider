JXActiveOscAddr {
	classvar stack;
	*initClass { stack = List[] }
	*get { ^stack.reduce('++').asSymbol ? '/' }
	*push {|o|
		JXTypeCheck(thisMethod, o -> Symbol);
		stack.add(o);
		^JXActiveOscAddr.get()
	}
	*pop { ^stack.pop }
	*with {|o, func|
		JXTypeCheck(thisMethod, o -> Symbol);
		stack.add(o);
		func.();
		stack.pop;
	}
}

JXActiveGroup {
	classvar stack;
	*initClass { stack = List[] }
	*get { ^stack.last ?? {Server.default} }
	*push {|o| JXTypeCheck(thisMethod, o -> JXGroup); stack.add(o); ^JXActiveGroup.get()}
	*pop { ^JXActiveGroup.stack.pop }
	*with {|o, func|
		JXTypeCheck(thisMethod, o -> JXGroup);
		stack.add(o);
		func.();
		stack.pop;
	}
}
