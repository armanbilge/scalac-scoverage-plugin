package scoverage

import java.io.File

import scala.collection.mutable

/**
 * @author Stephen Samuel */
case class Coverage()
  extends CoverageMetrics
  with MethodBuilders
  with java.io.Serializable
  with ClassBuilders
  with PackageBuilders
  with FileBuilders {

  private val statementsById = mutable.Map[Int, Statement]()
  override def statements = statementsById.values
  def add(stmt: Statement): Unit = statementsById.put(stmt.id, stmt)

  def avgClassesPerPackage = classCount / packageCount.toDouble
  def avgClassesPerPackageFormatted: String = "%.2f".format(avgClassesPerPackage)

  def avgMethodsPerClass = methodCount / classCount.toDouble
  def avgMethodsPerClassFormatted: String = "%.2f".format(avgMethodsPerClass)

  def loc = files.map(_.loc).sum
  def linesPerFile = loc / fileCount.toDouble
  def linesPerFileFormatted: String = "%.2f".format(linesPerFile)

  // returns the classes by least coverage
  def risks(limit: Int) = classes.toSeq.sortBy(_.statementCount).reverse.sortBy(_.statementCoverage).take(limit)

  def apply(ids: Iterable[Int]): Unit = ids foreach invoked
  def invoked(id: Int): Unit = statementsById.get(id).foreach(_.invoked())
}

trait MethodBuilders {
  def statements: Iterable[Statement]
  def methods: Seq[MeasuredMethod] = {
    statements.groupBy(stmt => stmt.location.packageName + "/" + stmt.location.className + "/" + stmt.location.method)
      .map(arg => MeasuredMethod(arg._1, arg._2))
      .toSeq
  }
  def methodCount = methods.size
}

trait PackageBuilders {
  def statements: Iterable[Statement]
  def packageCount = packages.size
  def packages: Seq[MeasuredPackage] = {
    statements.groupBy(_.location.packageName).map(arg => MeasuredPackage(arg._1, arg._2)).toSeq.sortBy(_.name)
  }
}

trait ClassBuilders {
  def statements: Iterable[Statement]
  def classes = statements.groupBy(_.location.className).map(arg => MeasuredClass(arg._1, arg._2))
  def classCount: Int = classes.size
}

trait FileBuilders {
  def statements: Iterable[Statement]
  def files: Iterable[MeasuredFile] = statements.groupBy(_.source).map(arg => MeasuredFile(arg._1, arg._2))
  def fileCount: Int = files.size
}

case class MeasuredMethod(name: String, statements: Iterable[Statement]) extends CoverageMetrics

case class MeasuredClass(name: String, statements: Iterable[Statement])
  extends CoverageMetrics with MethodBuilders {
  def source: String = statements.head.source
  def simpleName = name.split('.').last
  def loc = statements.map(_.line).max
}

case class MeasuredPackage(name: String, statements: Iterable[Statement])
  extends CoverageMetrics with ClassCoverage with ClassBuilders with FileBuilders {
}

case class MeasuredFile(source: String, statements: Iterable[Statement])
  extends CoverageMetrics with ClassCoverage with ClassBuilders {
  def filename = new File(source).getName
  def loc = statements.map(_.line).max
}

case class Statement(source: String,
                     location: Location,
                     id: Int,
                     start: Int,
                     end: Int,
                     line: Int,
                     desc: String,
                     symbolName: String,
                     treeName: String,
                     branch: Boolean,
                     var count: Int = 0) extends java.io.Serializable {
  def invoked(): Unit = count = count + 1
  def isInvoked = count > 0
}

sealed trait ClassType
object ClassType {
  case object Object extends ClassType
  case object Class extends ClassType
  case object Trait extends ClassType
  def fromString(str: String): ClassType = {
    str.toLowerCase match {
      case "object" => Object
      case "trait" => Trait
      case _ => Class
    }
  }
}

case class ClassRef(name: String) {
  lazy val simpleName = name.split(".").last
  lazy val getPackage = name.split(".").dropRight(1).mkString(".")
}

object ClassRef {
  def fromFilepath(path: String) = ClassRef(path.replace('/', '.'))
  def apply(_package: String, className: String): ClassRef = ClassRef(_package.replace('/', '.') + "." + className)
}

trait CoverageMetrics {
  def statements: Iterable[Statement]
  def statementCount: Int = statements.size
  def invokedStatements: Iterable[Statement] = statements.filter(_.count > 0)
  def invokedStatementCount = invokedStatements.size
  def statementCoverage: Double = if (statementCount == 0) 1 else invokedStatementCount / statementCount.toDouble
  def statementCoveragePercent = statementCoverage * 100
  def statementCoverageFormatted: String = "%.2f".format(statementCoveragePercent)
  def branches: Iterable[Statement] = statements.filter(_.branch)
  def branchCount: Int = branches.size
  def branchCoveragePercent = branchCoverage * 100
  def invokedBranches: Iterable[Statement] = branches.filter(_.count > 0)
  def invokedBranchesCount = invokedBranches.size

  /**
   * @see http://stackoverflow.com/questions/25184716/scoverage-ambiguous-measurement-from-branch-coverage
   */
  def branchCoverage: Double = {
    // if there are zero branches, then we have a single line of execution.
    // in that case, if there is at least some coverage, we have covered the branch.
    // if there is no coverage then we have not covered the branch
    if (branchCount == 0) {
      if (statementCoverage > 0) 1
      else 0
    } else {
      invokedBranchesCount / branchCount.toDouble
    }
  }
  def branchCoverageFormatted: String = "%.2f".format(branchCoveragePercent)
}

trait ClassCoverage {
  this: ClassBuilders =>
  val statements: Iterable[Statement]
  def invokedClasses: Int = classes.count(_.statements.count(_.count > 0) > 0)
  def classCoverage: Double = invokedClasses / classes.size.toDouble
}
