package pasito.staticEnvironment;

public class AlreadyBoundException extends Exception {
    public String log;
    public AlreadyBoundException() {
        log = " j� foi declarado(a)";
    }
    
    @Override
    public String getMessage() {
    	return log;
    }
}
