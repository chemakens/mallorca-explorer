const { onSchedule } = require("firebase-functions/v2/scheduler");
const { logger } = require("firebase-functions");
const admin = require("firebase-admin");

admin.initializeApp();
const db = admin.firestore();
const messaging = admin.messaging();

/**
 * Se ejecuta cada día a las 9:00 AM (hora de Madrid, UTC+1/+2).
 * - Lee los eventos de mañana en Firestore
 * - Por cada usuario con token FCM, filtra según sus categorías activas
 * - Manda una sola notificación resumen (sin spam)
 */
exports.dailyEventNotification = onSchedule(
  {
    schedule: "0 7 * * *", // 9am Madrid = 7am UTC (verano). Ajusta a "0 8 * * *" en invierno.
    timeZone: "Europe/Madrid",
    region: "europe-west1",
  },
  async () => {
    logger.info("🔔 Iniciando notificaciones diarias de eventos");

    // 1. Calcular la fecha de mañana (en Mallorca)
    const tomorrow = new Date();
    tomorrow.setDate(tomorrow.getDate() + 1);
    const tomorrowStr = tomorrow.toISOString().split("T")[0]; // "2026-09-21"
    logger.info(`Buscando eventos para: ${tomorrowStr}`);

    // 2. Obtener eventos de mañana
    const placesSnap = await db.collection("places").get();
    const tomorrowEvents = [];

    placesSnap.forEach((doc) => {
      const data = doc.data();
      // Los eventos tienen type === "event" y un campo date o start_date
      if (data.type !== "event") return;
      const eventDate = data.date || data.start_date || "";
      if (!eventDate.startsWith(tomorrowStr)) return;
      tomorrowEvents.push({
        id: doc.id,
        titleEs: data.title_es || data.title || "",
        category: data.category || "",
        municipality: data.municipality || "",
        isFree: data.is_free === true,
        price: data.price || "",
      });
    });

    logger.info(`Eventos encontrados para mañana: ${tomorrowEvents.length}`);

    if (tomorrowEvents.length === 0) {
      logger.info("Sin eventos mañana, no se manda nada.");
      return;
    }

    // 3. Obtener usuarios con token FCM
    const usersSnap = await db.collection("users").get();
    const sendPromises = [];
    let sent = 0;
    let skipped = 0;

    usersSnap.forEach((userDoc) => {
      const user = userDoc.data();
      const fcmToken = user.fcmToken;
      if (!fcmToken) { skipped++; return; }

      // 4. Filtrar eventos según las categorías del usuario
      // Si no tiene categorías guardadas = todas activas (comportamiento por defecto)
      const userCategories = user.notificationCategories || null;
      const filteredEvents = userCategories
        ? tomorrowEvents.filter((e) => userCategories.includes(e.category))
        : tomorrowEvents;

      if (filteredEvents.length === 0) { skipped++; return; }

      // 5. Construir el mensaje (una sola notificación)
      const title =
        filteredEvents.length === 1
          ? "🗓 Mañana en Mallorca"
          : `🗓 ${filteredEvents.length} eventos mañana en Mallorca`;

      const maxShown = 3;
      const names = filteredEvents.slice(0, maxShown).map((e) => e.titleEs);
      const body =
        filteredEvents.length > maxShown
          ? names.join(" · ") + ` y ${filteredEvents.length - maxShown} más`
          : names.join(" · ");

      const message = {
        token: fcmToken,
        notification: { title, body },
        android: {
          notification: {
            channelId: "mallorca_events",
            priority: "DEFAULT",
          },
        },
        apns: {
          payload: { aps: { sound: "default" } },
        },
      };

      sendPromises.push(
        messaging
          .send(message)
          .then(() => { sent++; })
          .catch((err) => {
            logger.warn(`Error enviando a ${userDoc.id}: ${err.message}`);
            // Si el token es inválido, eliminarlo de Firestore
            if (
              err.code === "messaging/registration-token-not-registered" ||
              err.code === "messaging/invalid-registration-token"
            ) {
              return db.collection("users").doc(userDoc.id).update({ fcmToken: admin.firestore.FieldValue.delete() });
            }
          })
      );
    });

    await Promise.all(sendPromises);
    logger.info(`✅ Notificaciones enviadas: ${sent}, omitidos: ${skipped}`);
  }
);
