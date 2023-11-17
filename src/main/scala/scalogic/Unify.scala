package scalogic.unify

type Term = Int | String | Boolean

extension (t: Term) {
  def withSubst(pat: Term, replacement: Term): Term = t match {
    case `pat` => replacement
    case _ => t
  }
}

def unify(t1: Term, t2: Term): Option[Substs] = (t1, t2) match {
    case (t1: Int, t2: Int) if t1 == t2 => Some(Map.empty)
    case (t1: String, t2: String) if t1 == t2 => Some(Map.empty)
    case (t1: String, t2) => Some(Map(t1 -> t2))
    case (t1, t2: String) => Some(Map(t2 -> t1))
    case _ => None
}

type Substs = Map[Term, Term]

case class Relation(argNames: List[String], body: Formula)

def conjunct(fs: Formula*): Formula = fs.reduceLeft(Formula.And(_, _))
def disjunct(fs: Formula*): Formula = fs.reduceLeft(Formula.Or(_, _))

enum Formula {
  case Eq(t1: Term, t2: Term)
  case And(f1: Formula, f2: Formula)
  case Or(f1: Formula, f2: Formula)
  case Not(f: Formula)
  case Fact(name: String, args: List[Term])
  case RelApp(name: String, args: List[Term])

  def withSubst(pat: Term, replacement: Term): Formula = this match {
    case Eq(t1, t2) => Eq(t1.withSubst(pat, replacement), t2.withSubst(pat, replacement))
    case And(f1, f2) => And(f1.withSubst(pat, replacement), f2.withSubst(pat, replacement))
    case Or(f1, f2) => Or(f1.withSubst(pat, replacement), f2.withSubst(pat, replacement))
    case Not(f) => Not(f.withSubst(pat, replacement))
    case Fact(name, args) => Fact(name, args.map(_.withSubst(pat, replacement)))
    case RelApp(name, args) => RelApp(name, args.map(_.withSubst(pat, replacement)))
  }

  def withSubsts(substs: Substs): Formula = substs.foldLeft(this) {
    case (f, (pat, replacement)) => f.withSubst(pat, replacement)
  }

  def solve(using facts: Set[Fact], relations: Map[String, Relation]): Option[Substs] = this match {
    case Eq(t1, t2) => unify(t1, t2)
    case And(f1, f2) => for {
      substs1 <- f1.solve
      substs2 <- f2.withSubsts(substs1).solve
    } yield substs1 ++ substs2
    case Or(f1, f2) => f1.solve.orElse(f2.solve)
    case Not(f) => if f.solve.isEmpty then Some(Map.empty) else None // doesn't work
    case Fact(name, args) => {
      val validSubsts = for {
        fact <- facts.filter(_.name == name).filter(_.args.length == args.length)
        fargs = fact.args
        substs <- conjunct(args.zip(fargs).map({ case (x, y) => Eq(x, y) })*).solve
      } yield substs
      validSubsts.headOption
    }
    case RelApp(name, args) => {
      val Relation(argNames, body) = relations(name)
      val substs = argNames.zip(args).toMap[Term, Term]
      val newBindings = body.withSubsts(substs).solve
      newBindings.map(_.filterKeys(argNames.contains).toMap)
    }
  }

  case class Res(substs: Option[Substs]) {
    override def toString: String = substs match {
      case Some(m) if m.isEmpty => "true"
      case None => "false"
      case Some(m) => m.toString
    }
  }

  def ?(using facts: Set[Fact], relations: Map[String, Relation]): Res = Res(solve)
}
