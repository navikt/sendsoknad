package no.nav.sbl.dialogarena.soknadinnsending.business.db;


import no.nav.sbl.dialogarena.sendsoknad.domain.*;
import no.nav.sbl.dialogarena.sendsoknad.domain.oppsett.FaktumStruktur;
import no.nav.sbl.dialogarena.sendsoknad.domain.oppsett.VedleggForFaktumStruktur;
import no.nav.sbl.dialogarena.soknadinnsending.business.db.soknad.HendelseRepository;
import no.nav.sbl.dialogarena.soknadinnsending.business.db.soknad.SoknadRepository;
import org.joda.time.DateTime;
import org.joda.time.DateTimeUtils;
import org.joda.time.Interval;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import javax.inject.Inject;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static java.util.Collections.singletonList;
import static java.util.Collections.sort;
import static java.util.UUID.randomUUID;
import static no.nav.sbl.dialogarena.sendsoknad.domain.Faktum.FaktumType.BRUKERREGISTRERT;
import static no.nav.sbl.dialogarena.sendsoknad.domain.Faktum.FaktumType.SYSTEMREGISTRERT;
import static no.nav.sbl.dialogarena.sendsoknad.domain.HendelseType.AVBRUTT_AV_BRUKER;
import static org.joda.time.DateTime.now;
import static org.junit.Assert.*;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = {DbTestConfig.class})
public class SoknadRepositoryJdbcTest {

    @Inject
    private SoknadRepository soknadRepository;
    @Inject
    private HendelseRepository hendelseRepository;
    @Inject
    private RepositoryTestSupport soknadRepositoryTestSupport;

    private WebSoknad soknad;
    private Long soknadId;

    private static final String AKTOR_ID = "1";
    private static final String BEHANDLINGS_ID = "1";
    private static final int VERSJONSNR = 1;
    private static final String SKJEMA_NUMMER = "skjemaNummer";
    private static final String UUID = "123";

    @After
    public void cleanUp() {
        soknadRepositoryTestSupport.getJdbcTemplate().update("delete from Vedlegg");
        soknadRepositoryTestSupport.getJdbcTemplate().update("delete from faktumegenskap");
        soknadRepositoryTestSupport.getJdbcTemplate().update("delete from soknadbrukerdata");
        soknadRepositoryTestSupport.getJdbcTemplate().update("delete from Soknad");
        soknadRepositoryTestSupport.getJdbcTemplate().update("delete from Hendelse");
    }

    @Test
    public void skalKunneOppretteSoknad() {
        opprettOgPersisterSoknad();
    }

    @Test
    public void skalSetteSistLagret() {
        DateTimeUtils.setCurrentMillisFixed(new Date().getTime());

        opprettOgPersisterSoknad();
        soknadRepository.settSistLagretTidspunkt(soknadId);
        WebSoknad endret = soknadRepository.hentSoknad(soknadId);
        System.out.println(new DateTime());
        System.out.println(new DateTime(endret.getSistLagret()));
        Interval endretIntervall = new Interval(new DateTime().minusMillis(1000), new DateTime().plusMillis(1000));
        assertTrue(endretIntervall.contains(endret.getSistLagret()));
    }

    @Test(expected = DataIntegrityViolationException.class)
    public void skalIkkeKunneOppretteUtenAktorId() {
        soknad = WebSoknad.startSoknad()
                .medUuid(UUID)
                .medBehandlingId(BEHANDLINGS_ID)
                .medskjemaNummer(SKJEMA_NUMMER)
                .medOppretteDato(now());

        soknadRepository.opprettSoknad(soknad);
    }

    @Test(expected = DataIntegrityViolationException.class)
    public void skalIkkeKunneOppretteUtenBehandlingId() {
        soknad = WebSoknad.startSoknad()
                .medUuid(UUID)
                .medAktorId(AKTOR_ID)
                .medskjemaNummer(SKJEMA_NUMMER)
                .medOppretteDato(now());

        soknadRepository.opprettSoknad(soknad);
    }

    @Test(expected = DataIntegrityViolationException.class)
    public void skalIkkeKunneOppretteUtenskjemaNummer() {
        soknad = WebSoknad.startSoknad()
                .medUuid(UUID)
                .medAktorId(AKTOR_ID)
                .medBehandlingId(BEHANDLINGS_ID)
                .medOppretteDato(now());

        soknadRepository.opprettSoknad(soknad);
    }

    @Test
    public void skalKunneHenteOpprettetSoknad() {
        opprettOgPersisterSoknad();

        WebSoknad opprettetSoknad = soknadRepository.hentSoknad(soknadId);
        assertNotNull(opprettetSoknad);
        assertEquals(SoknadInnsendingStatus.UNDER_ARBEID, opprettetSoknad.getStatus());
        assertEquals(AKTOR_ID, opprettetSoknad.getAktoerId());
        assertEquals(BEHANDLINGS_ID, opprettetSoknad.getBrukerBehandlingId());
        assertEquals(SKJEMA_NUMMER, opprettetSoknad.getskjemaNummer());
    }

    @Test
    public void skalKunneHenteOpprettetSoknadMedBehandlingsId() {
        String behId = randomUUID().toString();
        opprettOgPersisterSoknad(behId, "aktor-3");

        WebSoknad opprettetSoknad = soknadRepository.hentSoknad(behId);

        assertNotNull(opprettetSoknad);
        assertEquals(SoknadInnsendingStatus.UNDER_ARBEID, opprettetSoknad.getStatus());
        assertEquals("aktor-3", opprettetSoknad.getAktoerId());
        assertEquals(behId, opprettetSoknad.getBrukerBehandlingId());
        assertEquals(SKJEMA_NUMMER, opprettetSoknad.getskjemaNummer());
    }

    @Test
    public void skalFaaNullVedUkjentBehandlingsId() {
        String behId = randomUUID().toString();
        WebSoknad soknad = soknadRepository.hentSoknad(behId);
        Assert.assertNull(soknad);
    }

    @Test
    public void skalKunneLagreBrukerData() {
        String key = "Key";
        String value = "Value";

        opprettOgPersisterSoknad();
        lagreData(key, null, value);
    }

    @Test
    public void skalKunneHenteLagretBrukerData() {
        opprettOgPersisterSoknad();
        lagreData("key1", null, "value1");
        lagreData("key2", null, "value2");
        lagreData("key3", null, "value3");


        List<Faktum> soknadBrukerData = soknadRepository.hentAlleBrukerData(BEHANDLINGS_ID);

        assertNotNull(soknadBrukerData);
        assertEquals(3, soknadBrukerData.size());
    }

    @Test
    public void skalHenteSystemfaktum() {
        Faktum faktum = new Faktum().medKey("personalia").medSoknadId(12L).medProperty("fno", "123").medType(SYSTEMREGISTRERT);
        Faktum result = new Faktum().medKey("personalia").medSoknadId(11L).medProperty("fno", "123").medType(SYSTEMREGISTRERT);
        result.setFaktumId(soknadRepository.opprettFaktum(11L, faktum, true));

        List<Faktum> personalia = soknadRepository.hentSystemFaktumList(11L, "personalia");
        assertEquals(result, personalia.get(0));
    }

    @Test(expected = EmptyResultDataAccessException.class)
    public void skalSletteFaktum() {
        opprettOgPersisterSoknad();
        Long id = lagreData("key", null, "value");
        Faktum faktum = soknadRepository.hentFaktum(id);
        assertNotNull(faktum);
        soknadRepository.slettBrukerFaktum(soknadId, id);
        soknadRepository.hentFaktum(id);
        fail("ikke slettet");
    }

    @Test
    public void skalKunneHenteFaktum() {
        opprettOgPersisterSoknad();
        lagreData("key1", null, "value1");
        Long faktumId = lagreData("key2", null, "value2");
        lagreData("key3", null, "value3");

        soknadRepository.hentFaktum(faktumId);
    }

    @Test
    public void skalKunneHenteVersjon() {
        opprettOgPersisterSoknad();
        int versjon = hendelseRepository.hentVersjon(BEHANDLINGS_ID);
        assertEquals(1, versjon);
    }

    @Test
    public void skalFinneBehandlingsIdTilSoknadFraFaktumId() {
        Long soknadId = opprettOgPersisterSoknad("123abc", "aktor");
        Long faktumId = lagreData(soknadId, "key", null, "value");
        String behandlingsIdTilFaktum = soknadRepository.hentBehandlingsIdTilFaktum(faktumId);
        assertEquals("123abc", behandlingsIdTilFaktum);
    }

    @Test
    public void skalReturnereNullHvisFaktumIdIkkeFinnes() {
        String behandlingsIdTilFaktum = soknadRepository.hentBehandlingsIdTilFaktum(999L);
        assertNull(behandlingsIdTilFaktum);
    }

    @Test
    public void skalReturnereAtVedleggErPaakrevdOmParentHarEnAvDependOnValues() {
        opprettOgPersisterSoknad();
        Faktum parentFaktum = new Faktum().medKey("key1").medValue("dependOnValue").medSoknadId(soknad.getSoknadId()).medType(BRUKERREGISTRERT);
        Long parentFaktumId = soknadRepository.opprettFaktum(soknad.getSoknadId(), parentFaktum);
        FaktumStruktur parentFaktumStruktur = new FaktumStruktur().medId("key1");

        Faktum faktum = new Faktum().medKey("key2").medValue("true").medSoknadId(soknad.getSoknadId()).medType(BRUKERREGISTRERT).medParrentFaktumId(parentFaktumId);
        soknadRepository.opprettFaktum(soknad.getSoknadId(), faktum);
        FaktumStruktur faktumStruktur = new FaktumStruktur().medId("key2").medDependOn(parentFaktumStruktur).medDependOnValues(Arrays.asList("true", "dependOnValue"));
        VedleggForFaktumStruktur vedlegg = new VedleggForFaktumStruktur().medFaktum(faktumStruktur).medOnValues(singletonList("true"));

        Boolean vedleggPaakrevd = soknadRepository.isVedleggPaakrevd(soknadId, vedlegg);
        Assert.assertTrue(vedleggPaakrevd);
    }

    @Test
    public void skalReturnereAtVedleggIkkeErPaakrevdOmParentIkkeHarEnAvDependOnValues() {
        opprettOgPersisterSoknad();
        Faktum parentFaktum = new Faktum().medKey("key1").medValue("false").medSoknadId(soknad.getSoknadId()).medType(BRUKERREGISTRERT);
        Long parentFaktumId = soknadRepository.opprettFaktum(soknad.getSoknadId(), parentFaktum);
        FaktumStruktur parentFaktumStruktur = new FaktumStruktur().medId("key1");

        Faktum faktum = new Faktum().medKey("key2").medValue("true").medSoknadId(soknad.getSoknadId()).medType(BRUKERREGISTRERT).medParrentFaktumId(parentFaktumId);
        soknadRepository.opprettFaktum(soknad.getSoknadId(), faktum);
        FaktumStruktur faktumStruktur = new FaktumStruktur().medId("key2").medDependOn(parentFaktumStruktur).medDependOnValues(Arrays.asList("true", "dependOnValue"));
        VedleggForFaktumStruktur vedlegg = new VedleggForFaktumStruktur().medFaktum(faktumStruktur);

        Boolean vedleggPaakrevd = soknadRepository.isVedleggPaakrevd(soknadId, vedlegg);
        assertFalse(vedleggPaakrevd);
    }

    @Test
    public void skalReturnereAtVedleggErPaakrevdNaarParentOgParentParentErSattOgHarRettVerdi() {
        opprettOgPersisterSoknad();
        Faktum parentParentFaktum = new Faktum().medKey("parentParent").medValue("parentParentValue").medSoknadId(soknad.getSoknadId()).medType(BRUKERREGISTRERT);
        soknadRepository.opprettFaktum(soknad.getSoknadId(), parentParentFaktum);
        FaktumStruktur parentParentFaktumStruktur = new FaktumStruktur().medId("parentParent");

        Faktum parentFaktum = new Faktum().medKey("parent").medValue("parentValue").medSoknadId(soknad.getSoknadId()).medType(BRUKERREGISTRERT);
        Long parentFaktumId = soknadRepository.opprettFaktum(soknad.getSoknadId(), parentFaktum);
        FaktumStruktur parentFaktumStruktur = new FaktumStruktur().medId("parent").medDependOn(parentParentFaktumStruktur).medDependOnValues(singletonList("parentParentValue"));

        Faktum faktum = new Faktum().medKey("key").medValue("true").medSoknadId(soknad.getSoknadId()).medType(BRUKERREGISTRERT).medParrentFaktumId(parentFaktumId);
        soknadRepository.opprettFaktum(soknad.getSoknadId(), faktum);
        FaktumStruktur faktumStruktur = new FaktumStruktur().medId("key").medDependOn(parentFaktumStruktur).medDependOnValues(singletonList("parentValue"));
        VedleggForFaktumStruktur vedlegg = new VedleggForFaktumStruktur().medFaktum(faktumStruktur).medOnValues(singletonList("true"));

        Boolean vedleggPaakrevd = soknadRepository.isVedleggPaakrevd(soknadId, vedlegg);
        Assert.assertTrue(vedleggPaakrevd);
    }

    @Test
    public void skalReturnereAtVedleggIkkeErPaakrevdNaarParentParentIkkeHarRettVerdi() {
        opprettOgPersisterSoknad();
        Faktum parentParentFaktum = new Faktum().medKey("parentParent").medValue("false").medSoknadId(soknad.getSoknadId()).medType(BRUKERREGISTRERT);
        soknadRepository.opprettFaktum(soknad.getSoknadId(), parentParentFaktum);
        FaktumStruktur parentParentFaktumStruktur = new FaktumStruktur().medId("parentParent");

        Faktum parentFaktum = new Faktum().medKey("parent").medValue("parentValue").medSoknadId(soknad.getSoknadId()).medType(BRUKERREGISTRERT);
        Long parentFaktumId = soknadRepository.opprettFaktum(soknad.getSoknadId(), parentFaktum);
        FaktumStruktur parentFaktumStruktur = new FaktumStruktur().medId("parent").medDependOn(parentParentFaktumStruktur).medDependOnValues(singletonList("parentParentValue"));

        Faktum faktum = new Faktum().medKey("key").medValue("true").medSoknadId(soknad.getSoknadId()).medType(BRUKERREGISTRERT).medParrentFaktumId(parentFaktumId);
        soknadRepository.opprettFaktum(soknad.getSoknadId(), faktum);
        FaktumStruktur faktumStruktur = new FaktumStruktur().medId("key").medDependOn(parentFaktumStruktur).medDependOnValues(singletonList("parentValue"));
        VedleggForFaktumStruktur vedlegg = new VedleggForFaktumStruktur().medFaktum(faktumStruktur);

        Boolean vedleggPaakrevd = soknadRepository.isVedleggPaakrevd(soknadId, vedlegg);
        assertFalse(vedleggPaakrevd);
    }

    @Test
    public void skalTaVarePaaSystemproperties() {
        soknadId = opprettOgPersisterSoknad();
        soknadRepository.opprettFaktum(soknadId, new Faktum().medSoknadId(soknadId).medKey("system1").medType(SYSTEMREGISTRERT));
    }

    @Test
    public void skalHenteSoknadType() {
        opprettOgPersisterSoknad();
        String s = soknadRepository.hentSoknadType(soknadId);
        assertEquals(soknad.getskjemaNummer(), s);
    }

    @Test
    public void skalSetteDelstegstatus() {
        opprettOgPersisterSoknad();
        soknadRepository.settDelstegstatus(soknadId, DelstegStatus.SAMTYKKET);
        assertEquals(DelstegStatus.SAMTYKKET, soknadRepository.hentSoknad(soknadId).getDelstegStatus());
    }

    @Test
    public void skalSetteDelstegstatusMedBehandlingsId() {
        opprettOgPersisterSoknad();
        soknadRepository.settDelstegstatus(BEHANDLINGS_ID, DelstegStatus.SAMTYKKET);
        assertEquals(DelstegStatus.SAMTYKKET, soknadRepository.hentSoknad(soknadId).getDelstegStatus());
    }

    @Test
    public void skalSetteJournalforendeEnhet() {
        opprettOgPersisterSoknad();
        soknadRepository.settJournalforendeEnhet(BEHANDLINGS_ID, "NAV EØS");
        assertEquals("NAV EØS", soknadRepository.hentSoknad(BEHANDLINGS_ID).getJournalforendeEnhet());
    }

    @Test
    public void skalKunneOppdatereLagretBrukerData() {
        String key = "key";
        String value = "value";
        String oppdatertValue = "oppdatert";

        opprettOgPersisterSoknad();
        Long faktumId = lagreData(key, null, value);


        Faktum ikkeOppdaterData = soknadRepository.hentAlleBrukerData(BEHANDLINGS_ID).get(0);
        assertNotNull(ikkeOppdaterData);
        assertEquals(value, ikkeOppdaterData.getValue());


        lagreData(key, faktumId, oppdatertValue);
        Faktum oppdaterData = soknadRepository.hentAlleBrukerData(BEHANDLINGS_ID).get(0);
        assertNotNull(oppdaterData);
        assertEquals(oppdatertValue, oppdaterData.getValue());
    }

    @Test
    public void skalKunneHenteSoknadMedBrukerData() {
        opprettOgPersisterSoknad();
        lagreData("key1", null, "value1");
        lagreData("key2", null, "value2");
        lagreData("key3", null, "value3");

        WebSoknad soknadMedData = soknadRepository.hentSoknadMedData(soknadId);

        assertNotNull(soknadMedData);
        assertNotNull(soknadMedData.getFakta());
        assertEquals(3, soknadMedData.getFakta().size());
    }

    @Test
    public void skalReturnereNullOmSoknadMedBehandlingsIdIkkeFinnes() {
        opprettOgPersisterSoknad();
        WebSoknad soknadMedData = soknadRepository.hentSoknadMedVedlegg("soknadSomIkkeFinnes");
        assertNull(soknadMedData);
    }

    @Test
    public void skalReturnereNullOmSoknadMedSoknadIdIkkeFinnes() {
        opprettOgPersisterSoknad();
        WebSoknad soknadMedData = soknadRepository.hentSoknadMedData(1000000000L);
        assertNull(soknadMedData);
    }

    @Test
    public void plukkerRiktigeSoknaderPaaTversAvAlleTraader() throws InterruptedException {
        List<Long> soknaderSomSkalMellomlagres = lagreXSoknader(15, 3);
        lagreXSoknader(5, 0); // legger til søknader som ikke skal taes med

        final List<Long> soknaderSomBleMellomlagret = Collections.synchronizedList(new ArrayList<>());
        int numberOfThreads = 10;
        ExecutorService threadpool = Executors.newFixedThreadPool(numberOfThreads);
        for (int i = 0; i < numberOfThreads; i++) {
            threadpool.submit((Callable<Void>) () -> {
                while (true) {
                    Optional<WebSoknad> soknad = soknadRepository.plukkSoknadTilMellomlagring();
                    if (soknad.isPresent()) {
                        soknaderSomBleMellomlagret.add(soknad.get().getSoknadId());
                    } else {
                        break;
                    }
                }
                return null;
            });
        }
        threadpool.shutdown();
        threadpool.awaitTermination(1, TimeUnit.MINUTES);

        sort(soknaderSomSkalMellomlagres);
        sort(soknaderSomBleMellomlagret);
        assertEquals(soknaderSomSkalMellomlagres, soknaderSomBleMellomlagret);
    }

    @Test
    public void skalKunneSletteSoknad() {
        opprettOgPersisterSoknad();
        soknadRepository.slettSoknad(soknad, AVBRUTT_AV_BRUKER);
        assertNull(soknadRepository.hentSoknad(soknadId));
    }

    @Test
    public void skalKunneLeggeTilbake() {
        opprettOgPersisterSoknad();
        lagreData("key1", null, "value1");
        lagreData("key2", null, "value2");

        WebSoknad soknad = soknadRepository.hentSoknadMedData(soknadId);
        soknadRepository.leggTilbake(soknad);
        List<Faktum> soknadBrukerData = soknadRepository.hentAlleBrukerData(BEHANDLINGS_ID);
        assertNotNull(soknadBrukerData);
    }

    @Test
    public void skalRepopulereDatabaseOgSetteSistLagret() {
        soknad = WebSoknad.startSoknad()
                .medId(101L)
                .medUuid(UUID)
                .medAktorId("123123")
                .medBehandlingId("AH123")
                .medskjemaNummer(SKJEMA_NUMMER)
                .medOppretteDato(now())
                .medVersjon(0)
                .leggTilFaktum(new Faktum().medSoknadId(101L).medFaktumId(11L).medKey("key1").medValue("val1").medType(BRUKERREGISTRERT).medProperty("test", "test"))
                .leggTilFaktum(new Faktum().medSoknadId(101L).medFaktumId(12L).medKey("key2").medValue("val2").medType(SYSTEMREGISTRERT).medProperty("test2", "test2"))
                .medVedlegg(singletonList(new Vedlegg(101L, 11L, "L6", Vedlegg.Status.LastetOpp).medOpprettetDato(System.currentTimeMillis())));

        soknadRepository.populerFraStruktur(soknad);
        WebSoknad res = soknadRepository.hentSoknadMedData(soknad.getSoknadId());

        soknad.getVedlegg().get(0).setOpprettetDato(res.getVedlegg().get(0).getOpprettetDato());
        soknad.setSistLagret(res.getSistLagret());

        assertEquals(soknad, res);
        assertNotNull(res.getSistLagret());
    }

    @Test
    public void skalKunneHenteUtEttersendingMedBehandlingskjedeId() {
        opprettOgPersisterEttersending();

        Optional<WebSoknad> res = soknadRepository.hentEttersendingMedBehandlingskjedeId(BEHANDLINGS_ID);

        assertTrue(res.isPresent());
        assertEquals(DelstegStatus.ETTERSENDING_OPPRETTET, res.get().getDelstegStatus());
    }

    @Test
    public void skalFaaNullDersomManProverAHenteEttersendingMedBehandlingskjedeIdOgDetIkkeFinnesNoen() {
        Optional<WebSoknad> res = soknadRepository.hentEttersendingMedBehandlingskjedeId(BEHANDLINGS_ID);

        assertFalse(res.isPresent());
    }

    @Test
    public void skalHenteBarnafaktumMedProperties() {
        opprettOgPersisterSoknad();

        Faktum parentFaktum = new Faktum().medSoknadId(soknadId).medType(BRUKERREGISTRERT).medKey("parent");
        soknadRepository.opprettFaktum(soknadId, parentFaktum);

        Faktum child1 = new Faktum().medParrentFaktumId(parentFaktum.getFaktumId())
                .medType(BRUKERREGISTRERT).medSoknadId(soknadId).medProperty("key", "value").medKey("child");
        Faktum child2 = new Faktum().medParrentFaktumId(parentFaktum.getFaktumId())
                .medType(BRUKERREGISTRERT).medSoknadId(soknadId).medProperty("key2", "value").medKey("child");
        Faktum child3 = new Faktum().medParrentFaktumId(parentFaktum.getFaktumId())
                .medType(BRUKERREGISTRERT).medSoknadId(soknadId).medProperty("key3", "value").medKey("child");

        Faktum ikkeChild1 = new Faktum().medSoknadId(soknadId).medType(BRUKERREGISTRERT).medProperty("key4", "value4").medKey("child");
        Faktum ikkeChild2 = new Faktum().medSoknadId(soknadId).medType(BRUKERREGISTRERT).medProperty("key5", "value5").medKey("child");
        Faktum ikkeChild3 = new Faktum().medSoknadId(soknadId).medType(BRUKERREGISTRERT).medProperty("key6", "value6").medKey("child");

        soknadRepository.opprettFaktum(soknadId, child1);
        soknadRepository.opprettFaktum(soknadId, child2);
        soknadRepository.opprettFaktum(soknadId, child3);
        soknadRepository.opprettFaktum(soknadId, ikkeChild1);
        soknadRepository.opprettFaktum(soknadId, ikkeChild2);
        soknadRepository.opprettFaktum(soknadId, ikkeChild3);

        List<Faktum> barneFaktum = soknadRepository.hentBarneFakta(soknadId, parentFaktum.getFaktumId());
        assertEquals(3, barneFaktum.size());
        assertTrue(barneFaktum.contains(child1));
        assertTrue(barneFaktum.contains(child2));
        assertTrue(barneFaktum.contains(child3));
        assertFalse(barneFaktum.contains(ikkeChild1));
        assertFalse(barneFaktum.contains(ikkeChild2));
        assertFalse(barneFaktum.contains(ikkeChild3));

        assertTrue(barneFaktum.get(0).getProperties().containsValue("value"));
    }



    private List<Long> lagreXSoknader(int antall, int timerSidenLagring) {
        List<Long> soknadsIder = new ArrayList<>(antall);
        for (int i = 0; i < antall; i++) {
            Long id = opprettOgPersisterSoknad();
            soknadRepositoryTestSupport.getJdbcTemplate().update("update soknad set sistlagret = CURRENT_TIMESTAMP - (INTERVAL '" + timerSidenLagring + "' HOUR) where soknad_id = ?", soknadId);
            soknadsIder.add(id);
        }
        return soknadsIder;
    }

    private Long opprettOgPersisterSoknad() {
        return opprettOgPersisterSoknad(BEHANDLINGS_ID, AKTOR_ID);
    }

    private void opprettOgPersisterEttersending() {
        soknad = WebSoknad.startEttersending(BEHANDLINGS_ID)
                .medUuid(UUID)
                .medAktorId(AKTOR_ID)
                .medDelstegStatus(DelstegStatus.ETTERSENDING_OPPRETTET)
                .medBehandlingskjedeId(BEHANDLINGS_ID)
                .medskjemaNummer(SKJEMA_NUMMER).medOppretteDato(now());
        soknadId = soknadRepository.opprettSoknad(soknad);
        soknad.setSoknadId(soknadId);
    }

    private Long opprettOgPersisterSoknad(String behId, String aktor) {
        soknad = WebSoknad.startSoknad()
                .medUuid(UUID)
                .medAktorId(aktor)
                .medBehandlingId(behId)
                .medVersjon(VERSJONSNR)
                .medDelstegStatus(DelstegStatus.OPPRETTET)
                .medskjemaNummer(SKJEMA_NUMMER).medOppretteDato(now());
        soknadId = soknadRepository.opprettSoknad(soknad);
        soknad.setSoknadId(soknadId);
        return soknadId;
    }

    private Long lagreData(String key, Long faktumId, String value) {
        return lagreData(soknadId, key, faktumId, value);
    }

    private Long lagreData(Long soknadId, String key, Long faktumId, String value) {
        if(faktumId != null){
            return soknadRepository.oppdaterFaktum(new Faktum().medSoknadId(soknadId).medFaktumId(faktumId).medKey(key).medValue(value).medType(BRUKERREGISTRERT));
        }
        return soknadRepository.opprettFaktum(soknadId, new Faktum().medSoknadId(soknadId).medKey(key).medValue(value).medType(BRUKERREGISTRERT));
    }
}
