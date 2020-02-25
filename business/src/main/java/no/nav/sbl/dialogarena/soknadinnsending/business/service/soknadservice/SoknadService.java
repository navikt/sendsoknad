package no.nav.sbl.dialogarena.soknadinnsending.business.service.soknadservice;

import no.nav.sbl.dialogarena.sendsoknad.domain.DelstegStatus;
import no.nav.sbl.dialogarena.sendsoknad.domain.Faktum;
import no.nav.sbl.dialogarena.sendsoknad.domain.HendelseType;
import no.nav.sbl.dialogarena.sendsoknad.domain.WebSoknad;
import no.nav.sbl.dialogarena.sendsoknad.domain.oppsett.SoknadStruktur;
import no.nav.sbl.dialogarena.soknadinnsending.business.WebSoknadConfig;
import no.nav.sbl.dialogarena.soknadinnsending.business.db.soknad.SoknadRepository;
import no.nav.sbl.dialogarena.soknadinnsending.consumer.fillager.FillagerService;
import no.nav.sbl.dialogarena.soknadinnsending.consumer.henvendelse.HenvendelseService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import javax.inject.Inject;
import javax.inject.Named;

@Component
public class SoknadService {

    @Inject
    @Named("soknadInnsendingRepository")
    private SoknadRepository lokalDb;

    @Inject
    private HenvendelseService henvendelseService;

    @Inject
    private EttersendingService ettersendingService;

    @Inject
    private FillagerService fillagerService;

    @Inject
    private WebSoknadConfig config;

    @Inject
    private SoknadDataFletter soknadDataFletter;

    @Inject
    private SoknadMetricsService soknadMetricsService;

    public void settDelsteg(String behandlingsId, DelstegStatus delstegStatus) {
        lokalDb.settDelstegstatus(behandlingsId, delstegStatus);
    }

    public void settJournalforendeEnhet(String behandlingsId, String journalforendeEnhet) {
        lokalDb.settJournalforendeEnhet(behandlingsId, journalforendeEnhet);
    }

    public WebSoknad hentSoknadFraLokalDb(long soknadId) {
        return lokalDb.hentSoknad(soknadId);
    }

    public SoknadStruktur hentSoknadStruktur(String skjemanummer) {
        return config.hentStruktur(skjemanummer);
    }

    public WebSoknad hentEttersendingForBehandlingskjedeId(String behandlingsId) {
        return lokalDb.hentEttersendingMedBehandlingskjedeId(behandlingsId).orElse(null);
    }

    @Transactional
    public String startSoknad(String skjemanummer, String aktorId) {
        return soknadDataFletter.startSoknad(skjemanummer, aktorId);
    }

    @Transactional
    public void avbrytSoknad(String behandlingsId) {
        WebSoknad soknad = lokalDb.hentSoknad(behandlingsId);

        /*
         * Sletter alle vedlegg til søknader som blir avbrutt.
         * Dette burde egentlig gjøres i henvendelse, siden vi uansett skal slette alle vedlegg på avbrutte søknader.
         * I tillegg blir det liggende igjen mange vedlegg for søknader som er avbrutt før dette kallet ble lagt til.
         */
        fillagerService.slettAlle(soknad.getBrukerBehandlingId());
        henvendelseService.avbrytSoknad(soknad.getBrukerBehandlingId());
        lokalDb.slettSoknad(soknad, HendelseType.AVBRUTT_AV_BRUKER);

        soknadMetricsService.avbruttSoknad(soknad.getskjemaNummer(), soknad.erEttersending());
    }

    public String startEttersending(String behandlingsIdSoknad, String aktorId) {
        return ettersendingService.start(behandlingsIdSoknad, aktorId);
    }

    public WebSoknad hentSoknad(String behandlingsId, boolean medData, boolean medVedlegg) {
        return soknadDataFletter.hentSoknad(behandlingsId, medData, medVedlegg);
    }

    public Faktum hentSprak(long soknadId) {
        return lokalDb.hentFaktumMedKey(soknadId, "skjema.sprak");
    }

    public Long hentOpprinneligInnsendtDato(String behandlingsId) {
        return soknadDataFletter.hentOpprinneligInnsendtDato(behandlingsId);
    }

    public String hentSisteInnsendteBehandlingsId(String behandlingsId) {
        return soknadDataFletter.hentSisteInnsendteBehandlingsId(behandlingsId);
    }

    @Transactional
    public void sendSoknad(String behandlingsId, byte[] pdf) {
        sendSoknad(behandlingsId, pdf, null);
    }

    @Transactional
    public void sendSoknad(String behandlingsId, byte[] soknadPdf, byte[] fullSoknad) {
        soknadDataFletter.sendSoknad(behandlingsId, soknadPdf, fullSoknad);
    }
}
