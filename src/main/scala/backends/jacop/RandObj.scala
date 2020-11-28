package backends.jacop

import org.jacop.core.IntDomain
import org.jacop.scala.Model
import org.jacop.search.{
  DepthFirstSearch,
  IndomainRandom,
  PrintOutListener,
  SelectChoicePoint,
  SimpleSelect,
  SimpleSolutionListener,
  SolutionListener
}

import scala.collection.mutable

object RandObj {

  private val addLabelFun = new ThreadLocal[mutable.Buffer[DepthFirstSearch[_ <: org.jacop.core.Var]]]

  private def dfs[A <: Rand]: DepthFirstSearch[A] = {
    val label = new DepthFirstSearch[A]
    label.setAssignSolution(true)
    label.setSolutionListener(new PrintOutListener[A]())
    label
  }

  private def satisfySearch[A <: Rand](
    select:   SelectChoicePoint[A],
    listener: SolutionListener[A],
    model:    Model
  ): Boolean = {
    model.imposeAllConstraints()
    val label = dfs[A]
    label.setAssignSolution(true)
    label.setPrintInfo(false)
    addLabel(label)
    label.setSolutionListener(listener)
    listener.searchAll(false)
    label.labeling(model, select)
  }

  private def addLabel(label: DepthFirstSearch[_ <: Rand]): Unit = {
    val b = addLabelFun.get()
    if (b != null) b += label
  }
}

class RandObj(r: Int = new util.Random().nextInt(), implicit val model: Model = new Model) extends crv.RandObj {

  // We need a reference to the Parent RandomObj in order to enable or disable a constraint
  implicit val current: RandObj = this
  private var nOfCalls = 0
  private val listener = new SimpleSolutionListener[backends.jacop.Rand]
  private val domainDatabase = mutable.Map[Rand, IntDomain]()
  private var problemVariables = List[Rand]()
  private var initialize = false

  /**
    * Restore the domain of all [[Rand]] variable declared in the current [[RandObj]] to their initial values
    */
  private def resetDomains(): Unit = {
    domainDatabase.foreach(k => k._1.domain.setDomain(k._2))
  }

  /**
    * Print all the random variables declared inside the current [[RandObj]]
    */
  def debug(): Unit = {
    problemVariables.foreach(println)
  }

  /**
    * This method is called only the first time we randomize the current [[RandObj]]
    * This is necessary because every time we assign a solution to each of the random variables, their domains are
    * shrink
    */
  private def initializeObject(): Unit = {
    initialize = true
    problemVariables = model.vars.filter(x => x.isInstanceOf[Rand]).map(_.asInstanceOf[Rand]).toList
    problemVariables.foreach(x => domainDatabase += (x -> x.domain.cloneLight()))
  }

  /**
    * Randomize the current [[RandObj]]
    *
    * @return Boolean the result of the current randomization
    */
  override def randomize: Boolean = {
    nOfCalls += 1
    if (!initialize) initializeObject()
    resetDomains()
    RandObj.satisfySearch(
      new SimpleSelect[Rand](problemVariables.toArray, null, new IndomainRandom[Rand](r + nOfCalls)),
      listener,
      model
    )
  }
}
