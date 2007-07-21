package us.lump.envelope.server.rmi;

import java.util.ArrayList;
import java.util.List;

/**
 * A command name.
 *
 * @author Troy Bowman
 * @version $Revision: 1.1 $
 */

public enum Cmd {

  // diag
  ping(false, Facet.Action),

  // security
  getChallenge(false, Facet.Security, Param.user_name, Param.public_key),
  authChallengeResponse(false, Facet.Security, Param.user_name, Param.challenge_response),

  // transaction
  listTransactions(Facet.Action, Param.year),

  // report
  getCategoryBalance(Facet.Report, Param.category, Param.year, Param.reconciled),
  getCategoryBalances(Facet.Report, Param.year, Param.reconciled),
  getAccountBalance(Facet.Report, Param.account, Param.year, Param.reconciled),
  getAccountBalances(Facet.Report, Param.year, Param.reconciled),

  // end list
  ;

  private final Facet facet;
  private final ArrayList<Param> params = new ArrayList<Param>();
  private final Boolean sessionRequired;

  Cmd(boolean sessionRequired, Facet facet, Param... params) {
    for (Param p : params) this.params.add(p);
    this.facet = facet;
    this.sessionRequired = sessionRequired;
  }

  Cmd(Facet facet, Param... params) {
    this(true, facet, params);
  }

  /**
   * This is the facet of the command.  It refers directly to the class of DAO that will be called.
   *
   * @return Facet
   */
  public Facet getFacet() {
    return facet;
  }

  /**
   * Get the list of params defined for this Cmd;
   *
   * @return the list of parameters
   */
  public List<Param> getParams() {
    return params;
  }

  /**
   * Whether this command requires a session be established.
   *
   * @return Boolean
   */
  public Boolean isSessionRequired() {
    return sessionRequired;
  }

}
