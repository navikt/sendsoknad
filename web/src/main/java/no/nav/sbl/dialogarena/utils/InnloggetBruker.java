package no.nav.sbl.dialogarena.utils;

import no.nav.modig.core.context.SubjectHandler;
import no.nav.sbl.dialogarena.sendsoknad.domain.personalia.Personalia;
import no.nav.sbl.dialogarena.soknadinnsending.business.person.PersonaliaBolk;
import org.slf4j.Logger;

import javax.inject.Inject;

import static org.slf4j.LoggerFactory.getLogger;

public class InnloggetBruker {

    @Inject
    private PersonaliaBolk personaliaBolk;

    private static final Logger logger = getLogger(InnloggetBruker.class);

    public Personalia hentPersonalia() {
        String fnr = SubjectHandler.getSubjectHandler().getUid();
        Personalia personalia = null;
        try {
            personalia = personaliaBolk.hentPersonalia(fnr);
        } catch (Exception e) {
            logger.error("Kunne ikke hente personalia");
        }
        return personalia;
    }
}
