package no.nav.sbl.dialogarena.soknadinnsending.business.batch;

import no.nav.metrics.MetricsFactory;
import no.nav.metrics.Timer;
import no.nav.sbl.dialogarena.common.suspend.SuspendServlet;
import no.nav.sbl.dialogarena.sendsoknad.domain.SoknadInnsendingStatus;
import no.nav.sbl.dialogarena.sendsoknad.domain.WebSoknad;
import no.nav.sbl.dialogarena.soknadinnsending.business.db.soknad.SoknadRepository;
import no.nav.sbl.dialogarena.soknadinnsending.consumer.fillager.FillagerService;
import no.nav.sbl.dialogarena.soknadinnsending.consumer.henvendelse.HenvendelseService;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.inject.Inject;
import javax.xml.bind.JAXB;
import java.io.ByteArrayInputStream;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static no.nav.sbl.dialogarena.sendsoknad.domain.HendelseType.LAGRET_I_HENVENDELSE;
import static org.slf4j.LoggerFactory.getLogger;

@Service
public class LagringsScheduler {

    private static final Logger logger = getLogger(LagringsScheduler.class);
    private static final int SCHEDULE_RATE_MS = 1000 * 60 * 60; // 1 time
    private static final int SCHEDULE_INTERRUPT_MS = 1000 * 60 * 10; // 10 min
    private DateTime batchStartTime;
    private int vellykket;
    private int feilet;

    @Inject
    private SoknadRepository soknadRepository;
    @Inject
    private FillagerService fillagerService;
    @Inject
    private HenvendelseService henvendelseService;

    @Scheduled(fixedRate = SCHEDULE_RATE_MS)
    public void mellomlagreSoknaderOgNullstillLokalDb() throws InterruptedException {
        batchStartTime = DateTime.now();
        vellykket = 0;
        feilet = 0;
        if (Boolean.parseBoolean(System.getProperty("sendsoknad.batch.enabled", "true"))) {
            logger.info("Starter flytting av søknader til henvendelse-jobb");
            Timer batchTimer = MetricsFactory.createTimer("debug.lagringsjobb");
            batchTimer.start();

            List<WebSoknad> feilListe = mellomlagre(batchTimer);
            leggTilbakeFeilende(feilListe);

            batchTimer.stop();
            batchTimer.addFieldToReport("vellykket", vellykket);
            batchTimer.addFieldToReport("feilet", feilet);
            batchTimer.report();
            logger.info("Jobb fullført: {} vellykket, {} feilet", vellykket, feilet);
        } else {
            logger.warn("Batch disabled. Må sette environment property sendsoknad.batch.enabled til true for å sette den på igjen");
        }
    }

    private void leggTilbakeFeilende(List<WebSoknad> feilListe) {
        for (WebSoknad soknad : feilListe) {
            try {
                soknadRepository.leggTilbake(soknad);
            } catch (Exception e1) {
                logger.error("Klarte ikke å legge tilbake søknad {}", soknad.getSoknadId(), e1);
            }
        }
    }

    private List<WebSoknad> mellomlagre(Timer metrikk) throws InterruptedException {
        List<WebSoknad> feilListe = new ArrayList<>();

        while (true) {
            Optional<WebSoknad> ows = soknadRepository.plukkSoknadTilMellomlagring();
            if (!ows.isPresent()) {
                break;
            }
            WebSoknad ws = ows.get();

            if (isPaabegyntEttersendelse(ws)) {
                if (!avbrytOgSlettEttersendelse(ws)) {
                    feilListe.add(ws);
                }
            } else {
                if (!lagreFilTilHenvendelseOgSlettILokalDb(ws)) {
                    feilListe.add(ws);
                }
            }
            // Avslutt prosessen hvis det er gått for lang tid. Tyder på at noe er nede.
            if (harGaattForLangTid()) {
                logger.warn("Jobben har kjørt i mer enn {} ms. Den blir derfor terminert", SCHEDULE_INTERRUPT_MS);
                metrikk.addFieldToReport("avbruttPgaTid", true);
                break;
            }
            if (!SuspendServlet.isRunning()) {
                logger.warn("Avbryter jobben da appen skal suspendes");
                metrikk.addFieldToReport("avbruttPgaAppErSuspendert", true);
                break;
            }
        }
        return feilListe;
    }

    private boolean avbrytOgSlettEttersendelse(WebSoknad soknad) throws InterruptedException {
        try {
            henvendelseService.avbrytSoknad(soknad.getBrukerBehandlingId());
            slettFiler(soknad);
            soknadRepository.slettSoknad(soknad, LAGRET_I_HENVENDELSE);

            vellykket++;
            return true;
        } catch (Exception e) {
            feilet++;
            logger.error("Avbryt feilet for ettersending {}.", soknad.getSoknadId(), e);
            Thread.sleep(1000); // Så loggen ikke blir fylt opp

            return false;
        }
    }

    private void slettFiler(WebSoknad soknad) {
        try {
            fillagerService.slettAlle(soknad.getBrukerBehandlingId());
        } catch (Exception e) {
            logger.error("Sletting av filer feilet for ettersending {}. Henvendelsen de hører til er satt til avbrutt, og ettersendingen slettes i sendsøknad.", soknad.getSoknadId(), e);
        }
    }

    private boolean isPaabegyntEttersendelse(WebSoknad soknad) {
        return soknad.erEttersending();
    }

    protected boolean lagreFilTilHenvendelseOgSlettILokalDb(WebSoknad soknad) throws InterruptedException {
        try {
            if (soknad.getStatus().equals(SoknadInnsendingStatus.UNDER_ARBEID) && !soknad.erEttersending()) {
                StringWriter xml = new StringWriter();
                JAXB.marshal(soknad, xml);
                fillagerService.lagreFil(soknad.getBrukerBehandlingId(), soknad.getUuid(), soknad.getAktoerId(), new ByteArrayInputStream(xml.toString().getBytes()));
            }
            soknadRepository.slettSoknad(soknad, LAGRET_I_HENVENDELSE);

            logger.info("Lagret soknad til henvendelse og slettet lokalt. Soknadsid: {}", soknad.getSoknadId());
            vellykket++;
            return true;
        } catch (Exception e) {
            feilet++;
            logger.error("Lagring eller sletting feilet for soknad {}", soknad.getSoknadId(), e);

            Thread.sleep(1000); // Så loggen ikke blir fylt opp
            return false;
        }
    }

    private boolean harGaattForLangTid() {
        return DateTime.now().isAfter(batchStartTime.plusMillis(SCHEDULE_INTERRUPT_MS));
    }
}
