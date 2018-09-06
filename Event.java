/* Protocol events. */
public enum Event {
  SND("snd"),
  RCV("rcv"),
  DROP("drop"),
  CORR("corr"),
  DUP("dup"),
  RORD("rord"),
  DELY("dely"),
  DA("DA"),
  RXT("RXT");

  private String code;

  Event(String code) {
      this.code = code;
  }

  public String toString() {
      return code;
  }
}