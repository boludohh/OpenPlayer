#include <jni.h>
#include <string>
#include <vector>
#include <map>
#include <fstream>
#include <android/log.h>
#include <taglib/fileref.h>
#include <taglib/tag.h>
#include <tpropertymap.h>
#include <taglib/audioproperties.h>
#include <mpegfile.h>
#include <id3v2tag.h>
#include <id3v2frame.h>
#include <id3v2header.h>
#include <attachedpictureframe.h>
#include <flacfile.h>
#include <flacpicture.h>
#include <oggfile.h>
#include <xiphcomment.h>
#include <mp4file.h>
#include <mp4coverart.h>
#include <mp4item.h>

#define LOG_TAG "OpenPlayerAudio"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace {

std::map<std::string, std::string> extractMetadataMap(const char* path) {
    std::map<std::string, std::string> metadata;

    TagLib::FileRef fileRef(path);
    if (fileRef.isNull() || !fileRef.tag()) {
        return metadata;
    }

    TagLib::Tag* tag = fileRef.tag();
    TagLib::PropertyMap properties = tag->properties();

    auto getProperty = [&properties](const char* key) -> std::string {
        if (properties.contains(key) && !properties[key].isEmpty()) {
            return properties[key].front().to8Bit(true);
        }
        return "";
    };

    metadata["title"] = getProperty("TITLE");
    metadata["artist"] = getProperty("ARTIST");
    metadata["album"] = getProperty("ALBUM");
    metadata["albumArtist"] = getProperty("ALBUMARTIST");
    metadata["genre"] = getProperty("GENRE");
    metadata["composer"] = getProperty("COMPOSER");
    metadata["lyrics"] = getProperty("LYRICS");

    metadata["trackNumber"] = getProperty("TRACKNUMBER");
    metadata["discNumber"] = getProperty("DISCNUMBER");
    metadata["year"] = getProperty("DATE");

    if (fileRef.audioProperties()) {
        TagLib::AudioProperties* audioProps = fileRef.audioProperties();
        metadata["duration"] = std::to_string(audioProps->lengthInMilliseconds());
        metadata["bitrate"] = std::to_string(audioProps->bitrate());
        metadata["sampleRate"] = std::to_string(audioProps->sampleRate());
        metadata["channels"] = std::to_string(audioProps->channels());
    }

    return metadata;
}

std::vector<unsigned char> extractCoverBytes(const char* path) {
    std::vector<unsigned char> coverData;

    TagLib::FileRef fileRef(path);
    if (fileRef.isNull()) {
        return coverData;
    }

    TagLib::File* file = fileRef.file();
    
    if (auto* mpegFile = dynamic_cast<TagLib::MPEG::File*>(file)) {
        if (mpegFile->ID3v2Tag()) {
            TagLib::ID3v2::FrameList frames = mpegFile->ID3v2Tag()->frameList("APIC");
            if (!frames.isEmpty()) {
                auto* pictureFrame = dynamic_cast<TagLib::ID3v2::AttachedPictureFrame*>(frames.front());
                if (pictureFrame) {
                    TagLib::ByteVector data = pictureFrame->picture();
                    coverData.assign(data.begin(), data.end());
                    return coverData;
                }
            }
        }
    }

    if (auto* flacFile = dynamic_cast<TagLib::FLAC::File*>(file)) {
        TagLib::List<TagLib::FLAC::Picture*> pictures = flacFile->pictureList();
        if (!pictures.isEmpty()) {
            TagLib::ByteVector data = pictures.front()->data();
            coverData.assign(data.begin(), data.end());
            return coverData;
        }
    }

    if (auto* oggFile = dynamic_cast<TagLib::Ogg::File*>(file)) {
        if (auto* xiphComment = dynamic_cast<TagLib::Ogg::XiphComment*>(oggFile->tag())) {
            TagLib::List<TagLib::FLAC::Picture*> pictures = xiphComment->pictureList();
            if (!pictures.isEmpty()) {
                TagLib::ByteVector data = pictures.front()->data();
                coverData.assign(data.begin(), data.end());
                return coverData;
            }
        }
    }

    if (auto* mp4File = dynamic_cast<TagLib::MP4::File*>(file)) {
        if (mp4File->tag()) {
            TagLib::MP4::ItemMap items = mp4File->tag()->itemMap();
            if (items.contains("covr")) {
                TagLib::MP4::CoverArtList covers = items["covr"].toCoverArtList();
                if (!covers.isEmpty()) {
                    TagLib::ByteVector data = covers.front().data();
                    coverData.assign(data.begin(), data.end());
                    return coverData;
                }
            }
        }
    }

    return coverData;
}

} // namespace

extern "C" JNIEXPORT jstring JNICALL
Java_com_openplayer_music_native_NativeBridge_getVersion(JNIEnv* env, jobject) {
    return env->NewStringUTF("OpenPlayerAudio 0.1.0 - TagLib 2.3.1");
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_openplayer_music_native_NativeBridge_isNativeAvailable(JNIEnv*, jobject) {
    return JNI_TRUE;
}

extern "C" JNIEXPORT jobject JNICALL
Java_com_openplayer_music_native_NativeBridge_extractMetadata(
    JNIEnv* env, jobject, jstring path) {
    
    const char* pathStr = env->GetStringUTFChars(path, nullptr);
    auto metadata = extractMetadataMap(pathStr);
    env->ReleaseStringUTFChars(path, pathStr);

    jclass hashMapClass = env->FindClass("java/util/HashMap");
    jmethodID hashMapInit = env->GetMethodID(hashMapClass, "<init>", "()V");
    jobject hashMap = env->NewObject(hashMapClass, hashMapInit);
    jmethodID hashMapPut = env->GetMethodID(hashMapClass, "put", 
        "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;");

    for (const auto& [key, value] : metadata) {
        jstring jKey = env->NewStringUTF(key.c_str());
        jstring jValue = env->NewStringUTF(value.c_str());
        env->CallObjectMethod(hashMap, hashMapPut, jKey, jValue);
        env->DeleteLocalRef(jKey);
        env->DeleteLocalRef(jValue);
    }

    return hashMap;
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_openplayer_music_native_NativeBridge_extractCoverBytes(
    JNIEnv* env, jobject, jstring path) {
    
    const char* pathStr = env->GetStringUTFChars(path, nullptr);
    auto coverData = extractCoverBytes(pathStr);
    env->ReleaseStringUTFChars(path, pathStr);

    if (coverData.empty()) {
        return nullptr;
    }

    jbyteArray result = env->NewByteArray(coverData.size());
    env->SetByteArrayRegion(result, 0, coverData.size(), 
        reinterpret_cast<const jbyte*>(coverData.data()));
    
    return result;
}